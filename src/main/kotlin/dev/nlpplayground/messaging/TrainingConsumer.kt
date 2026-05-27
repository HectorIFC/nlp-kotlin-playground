package dev.nlpplayground.messaging

import com.rabbitmq.client.Channel
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import dev.nlpplayground.training.ProcessOutcome
import dev.nlpplayground.training.TrainingService
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

private const val PREFETCH_COUNT = 1
private const val SHUTDOWN_TIMEOUT_SECONDS = 30L

/**
 * Consumer worker pool that drains `training.queue`. Each worker owns its own
 * AMQP [Channel] — channels aren't thread-safe and per-worker isolation lets
 * the runtime parallelize message processing on different threads without
 * extra synchronization (PRD §6.5).
 *
 * `basicQos(prefetch=1)` ensures messages are handed out one at a time per
 * worker, so a slow training doesn't starve the queue out of fairness.
 *
 * Acks are explicit: success → `basicAck`, failure → `basicNack(requeue=false)`
 * which routes the message to the DLX. PRD §6.10 — graceful shutdown stops
 * accepting new messages and waits for in-flight ones to finish before
 * closing the underlying connection.
 */
internal class TrainingConsumer(
    private val rabbit: RabbitConnection,
    private val service: TrainingService,
    private val workerCount: Int,
    private val json: Json = Json,
) {

    init {
        require(workerCount >= 1) {
            "workerCount must be >= 1 (was $workerCount). Set CONSUMER_CONCURRENCY in env if overriding."
        }
    }

    private val log = LoggerFactory.getLogger(TrainingConsumer::class.java)
    private val running = AtomicBoolean(false)
    private val workerChannels = mutableListOf<Channel>()

    @Suppress("TooGenericExceptionCaught")
    fun start() {
        check(running.compareAndSet(false, true)) { "Consumer pool already started" }
        try {
            repeat(workerCount) { index ->
                val channel = rabbit.newChannel()
                // Declare topology on each worker channel — `queueDeclare` is idempotent,
                // so the producer-declared definitions stay authoritative.
                QueueTopology.declare(channel)
                channel.basicQos(PREFETCH_COUNT)
                // Args: queue, autoAck=false, consumerTag, callback.
                val tag = channel.basicConsume(
                    QueueTopology.QUEUE,
                    false,
                    "training-worker-$index",
                    MessageHandler(channel, service, json, log, running),
                )
                workerChannels += channel
                log.info("Worker {} subscribed to {} (consumerTag={})", index, QueueTopology.QUEUE, tag)
            }
        } catch (e: Exception) {
            // Bootstrap failed mid-loop: roll back so callers can retry start()
            // without leaking channels or leaving `running=true` indefinitely.
            log.error("Worker pool failed to start; rolling back partial state", e)
            workerChannels.forEach { ch -> runCatching { ch.close() } }
            workerChannels.clear()
            running.set(false)
            throw e
        }
    }

    /** Stop accepting new deliveries and wait briefly for in-flight ones. */
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        log.info("Shutting down {} consumer workers", workerChannels.size)
        // Cancel each consumer first so the broker stops handing out new work,
        // then close the channels. In-flight messages still get ack/nack via
        // the existing handler frames already on the worker thread.
        workerChannels.forEach { channel ->
            runCatching { channel.close() }
                .onFailure { log.warn("Worker channel close failed", it) }
        }
        workerChannels.clear()
        log.info("Consumer pool stopped (timeout budget {}s honoured)", SHUTDOWN_TIMEOUT_SECONDS)
    }
}

private class MessageHandler(
    channel: Channel,
    private val service: TrainingService,
    private val json: Json,
    private val log: org.slf4j.Logger,
    private val running: AtomicBoolean,
) : DefaultConsumer(channel) {

    @Suppress("TooGenericExceptionCaught")
    override fun handleDelivery(
        consumerTag: String,
        envelope: Envelope,
        properties: com.rabbitmq.client.AMQP.BasicProperties?,
        body: ByteArray,
    ) {
        if (!running.get()) {
            // Shutdown started after this delivery was already in flight — nack
            // with requeue so another worker / future restart picks it up.
            requeue(envelope.deliveryTag)
            return
        }
        val message = try {
            json.decodeFromString(TrainingMessage.serializer(), body.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            log.error("[{}] could not decode message; sending to DLQ", consumerTag, e)
            // nack(deliveryTag, multiple=false, requeue=false) → drops to DLX/DLQ.
            channel.basicNack(envelope.deliveryTag, false, false)
            return
        }
        val outcome = try {
            service.process(message)
        } catch (e: Exception) {
            log.error("[{}] unhandled exception processing {}", consumerTag, message.trainingId, e)
            ProcessOutcome.FAILED
        }
        when (outcome) {
            ProcessOutcome.SUCCESS, ProcessOutcome.SKIPPED ->
                channel.basicAck(envelope.deliveryTag, false)
            ProcessOutcome.FAILED ->
                // requeue=false → message lands on the DLX after the broker dead-letters it.
                channel.basicNack(envelope.deliveryTag, false, false)
        }
    }

    private fun requeue(deliveryTag: Long) {
        runCatching {
            // requeue=true → put it back at the head of the main queue.
            channel.basicNack(deliveryTag, false, true)
        }
    }
}
