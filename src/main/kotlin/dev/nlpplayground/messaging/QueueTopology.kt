package dev.nlpplayground.messaging

import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Channel

/**
 * RabbitMQ topology used by the training pipeline.
 *
 * ```
 *   POST /upload ──> training.exchange (direct, durable)
 *                          │ routing key: training.requested
 *                          ▼
 *                    training.queue (durable, x-message-ttl=10m,
 *                          │           x-dead-letter-exchange=training.dlx)
 *                          ▼
 *                    TrainingConsumer (manual acks)
 *
 *   nack(noRequeue) / TTL expiry
 *                          │
 *                          ▼
 *                    training.dlx (fanout, durable)
 *                          │
 *                          ▼
 *                    training.dlq (durable, no consumer in v0.1.0 —
 *                                  inspected manually in management UI)
 * ```
 *
 * Everything is durable so a broker restart preserves both the messages
 * in flight and the routing wiring.
 *
 * Positional arguments to the AMQP client are commented in-line above each
 * call so callers don't need to keep the AMQP signature open in their head.
 */
internal object QueueTopology {

    const val EXCHANGE = "training.exchange"
    const val ROUTING_KEY = "training.requested"
    const val QUEUE = "training.queue"

    const val DLX = "training.dlx"
    const val DLQ = "training.dlq"

    private const val MESSAGE_TTL_MS = 600_000 // 10 minutes

    fun declare(channel: Channel) {
        // Exchanges — both durable.
        channel.exchangeDeclare(EXCHANGE, BuiltinExchangeType.DIRECT, true)
        channel.exchangeDeclare(DLX, BuiltinExchangeType.FANOUT, true)

        // Main queue: durable, non-exclusive, non-autoDelete. DLX + TTL via x-args.
        val mainQueueArgs = mapOf<String, Any>(
            "x-dead-letter-exchange" to DLX,
            "x-message-ttl" to MESSAGE_TTL_MS,
        )
        channel.queueDeclare(QUEUE, true, false, false, mainQueueArgs)
        channel.queueBind(QUEUE, EXCHANGE, ROUTING_KEY)

        // Dead-letter queue: durable, no args, bound to the fanout DLX (routing key ignored).
        channel.queueDeclare(DLQ, true, false, false, null)
        channel.queueBind(DLQ, DLX, "")
    }
}
