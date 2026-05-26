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
 * - [sessions] is the legacy in-memory store, kept until Fase 3 finishes
 *   migrating the explore routes to look up trainings via [trainings].
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

    fun close() {
        runCatching { consumer.stop() }
        runCatching { expirationScheduler.stop() }
        runCatching { rabbit.close() }
        // PlaygroundDatabase uses Exposed's static handle — Exposed cleans up via JVM shutdown hooks.
        // MinIO client is HTTP-based and stateless; nothing to close.
    }
}
