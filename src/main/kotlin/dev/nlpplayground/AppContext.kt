package dev.nlpplayground

import dev.nlpplayground.messaging.RabbitConnection
import dev.nlpplayground.messaging.TrainingPublisher
import dev.nlpplayground.persistence.PlaygroundDatabase
import dev.nlpplayground.persistence.TrainingEventRepository
import dev.nlpplayground.persistence.TrainingRepository
import dev.nlpplayground.pipeline.PipelineService
import dev.nlpplayground.session.SessionEvictionScheduler
import dev.nlpplayground.session.SessionStore
import dev.nlpplayground.storage.BlobStorage
import dev.nlpplayground.storage.MinioBlobStorage

/**
 * Wired-up runtime collaborators. One instance per JVM in production; tests
 * can build their own with stubs or hand-rolled fakes.
 *
 * Lifecycle:
 *
 * - [database], [storage], [rabbit] are opened on construction; the caller
 *   must invoke [close] on shutdown (`Application.kt` wires the hook).
 * - [scheduler] is started on application start and stopped on shutdown.
 * - [sessions] and [pipelineService] are plain in-memory values — they will
 *   be retired in Fase 3 once routes flip to use `TrainingRepository` directly.
 */
internal class AppContext(
    val config: Config = Config.fromEnv(),
    databaseFactory: (Config) -> PlaygroundDatabase = ::PlaygroundDatabase,
    storageFactory: (Config) -> BlobStorage = ::MinioBlobStorage,
    rabbitFactory: (Config) -> RabbitConnection = ::RabbitConnection,
    publisherFactory: (RabbitConnection) -> TrainingPublisher = ::TrainingPublisher,
    val sessions: SessionStore = SessionStore(),
    val pipelineService: PipelineService = PipelineService(),
) {

    val database: PlaygroundDatabase = databaseFactory(config)
    val storage: BlobStorage = storageFactory(config)
    val rabbit: RabbitConnection = rabbitFactory(config)
    val publisher: TrainingPublisher = publisherFactory(rabbit)
    val events: TrainingEventRepository = TrainingEventRepository(database.handle)
    val trainings: TrainingRepository = TrainingRepository(database.handle, events)
    val scheduler: SessionEvictionScheduler = SessionEvictionScheduler(sessions)

    fun close() {
        runCatching { rabbit.close() }
        runCatching { scheduler.stop() }
        // PlaygroundDatabase uses Exposed's static handle — Exposed cleans up via JVM shutdown hooks.
        // MinIO client is HTTP-based and stateless; nothing to close.
    }
}
