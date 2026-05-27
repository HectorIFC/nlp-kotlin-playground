package dev.nlpplayground

import dev.nlpplayground.messaging.RabbitConnection
import dev.nlpplayground.messaging.TrainingConsumer
import dev.nlpplayground.messaging.TrainingPublisher
import dev.nlpplayground.observability.MetricsRegistry
import dev.nlpplayground.persistence.PlaygroundDatabase
import dev.nlpplayground.persistence.TrainingEventRepository
import dev.nlpplayground.persistence.TrainingRepository
import dev.nlpplayground.storage.BlobStorage
import dev.nlpplayground.storage.MinioBlobStorage
import dev.nlpplayground.training.ExpirationScheduler
import dev.nlpplayground.training.TrainingPipelineLoader
import dev.nlpplayground.training.TrainingService
import org.slf4j.LoggerFactory

/**
 * Wired-up runtime collaborators. One instance per JVM in production; tests
 * can build their own with stubs or hand-rolled fakes.
 *
 * Lifecycle:
 *
 * - [database], [storage], [rabbit] are opened on construction; the caller
 *   must invoke [close] on shutdown (`Application.kt` wires the hook).
 * - [consumer] worker pool is started on `ApplicationStarted` and stopped on
 *   `ApplicationStopped` so in-flight messages have a chance to ack cleanly.
 * - [expirationScheduler] sweeps READY trainings past their TTL on a daemon thread.
 */
internal class AppContext(
    val config: Config = Config.fromEnv(),
    databaseFactory: (Config) -> PlaygroundDatabase = ::PlaygroundDatabase,
    storageFactory: (Config) -> BlobStorage = ::MinioBlobStorage,
    rabbitFactory: (Config) -> RabbitConnection = ::RabbitConnection,
    publisherFactory: (RabbitConnection) -> TrainingPublisher = ::TrainingPublisher,
) {

    val database: PlaygroundDatabase = databaseFactory(config)
    val storage: BlobStorage = storageFactory(config)
    val rabbit: RabbitConnection = rabbitFactory(config)
    val publisher: TrainingPublisher = publisherFactory(rabbit)
    val events: TrainingEventRepository = TrainingEventRepository(database.handle)
    val trainings: TrainingRepository = TrainingRepository(database.handle, events)
    val metrics: MetricsRegistry = MetricsRegistry()
    val trainingService: TrainingService = TrainingService(config, storage, trainings, metrics = metrics)
    val pipelineLoader: TrainingPipelineLoader = TrainingPipelineLoader(config, storage)
    val consumer: TrainingConsumer = TrainingConsumer(rabbit, trainingService, config.consumerConcurrency)
    val expirationScheduler: ExpirationScheduler = ExpirationScheduler(trainings, metrics)

    private val log = LoggerFactory.getLogger(AppContext::class.java)

    fun close() {
        // Log instead of silently swallowing: shutdown errors are usually
        // benign (already-closed channels, etc.) but a regression here would
        // otherwise vanish into the void.
        runCatching { consumer.stop() }
            .onFailure { e -> log.warn("Consumer shutdown failed", e) }
        runCatching { expirationScheduler.stop() }
            .onFailure { e -> log.warn("Expiration scheduler shutdown failed", e) }
        runCatching { rabbit.close() }
            .onFailure { e -> log.warn("RabbitMQ connection close failed", e) }
        runCatching { database.close() }
            .onFailure { e -> log.warn("Database pool close failed", e) }
        // MinIO client is HTTP-based and stateless; nothing to close.
    }
}
