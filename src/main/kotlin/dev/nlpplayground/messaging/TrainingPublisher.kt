package dev.nlpplayground.messaging

import com.rabbitmq.client.MessageProperties
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Publishes [TrainingMessage]s to RabbitMQ. Each message goes out as
 * `PERSISTENT_TEXT_PLAIN` so a broker restart doesn't lose pending work
 * (PRD §6.9 — durable queue alone isn't enough; the message itself also
 * needs the persistent flag).
 *
 * The publisher reuses a single [com.rabbitmq.client.Channel] per instance.
 * Channels aren't thread-safe, so route handlers serialize through this
 * publisher rather than allocating a channel per request.
 */
internal open class TrainingPublisher(private val rabbit: RabbitConnection, private val json: Json = Json) {

    private val log = LoggerFactory.getLogger(TrainingPublisher::class.java)

    private val channel by lazy {
        rabbit.newChannel().also { QueueTopology.declare(it) }
    }

    @Synchronized
    open fun publish(message: TrainingMessage) {
        val body = json.encodeToString(message).toByteArray(Charsets.UTF_8)
        channel.basicPublish(
            QueueTopology.EXCHANGE,
            QueueTopology.ROUTING_KEY,
            MessageProperties.PERSISTENT_TEXT_PLAIN,
            body,
        )
        log.info("Published training message id={}", message.trainingId)
    }
}
