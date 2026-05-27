package dev.nlpplayground.messaging

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import dev.nlpplayground.Config
import org.slf4j.LoggerFactory

/**
 * Single durable [Connection] for the JVM. RabbitMQ connections
 * are expensive; share one and create cheap [Channel]s per worker/handler.
 *
 * Queue topology (`training.exchange`, DLX, DLQ) is declared in Fase 2 via
 * [QueueTopology]. This class only owns the TCP-level connection.
 *
 * The connection is **lazy**: the first call to [connection], [newChannel] or
 * [isHealthy] triggers the actual TCP handshake. This lets tests instantiate
 * the class without a real broker.
 */
internal open class RabbitConnection(private val config: Config) {

    private val log = LoggerFactory.getLogger(RabbitConnection::class.java)

    // Keep an explicit Lazy<T> so close() can avoid forcing initialization
    // when the connection was never actually used.
    private val connectionDelegate: Lazy<Connection> = lazy {
        ConnectionFactory().apply {
            host = config.rabbitHost
            port = config.rabbitPort
            username = config.rabbitUser
            password = config.rabbitPass
            isAutomaticRecoveryEnabled = true
            requestedHeartbeat = HEARTBEAT_SECONDS
        }.newConnection().also { log.info("RabbitMQ connection opened to {}:{}", config.rabbitHost, config.rabbitPort) }
    }

    open val connection: Connection get() = connectionDelegate.value

    open fun newChannel(): Channel = connection.createChannel()

    open fun isHealthy(): Boolean = runCatching { connection.isOpen }
        .onFailure { e -> log.warn("RabbitMQ health check failed", e) }
        .getOrDefault(false)

    open fun close() {
        if (!connectionDelegate.isInitialized()) return
        runCatching { connectionDelegate.value.close() }
            .onFailure { e -> log.warn("Error closing RabbitMQ connection", e) }
    }

    private companion object {
        const val HEARTBEAT_SECONDS = 30
    }
}
