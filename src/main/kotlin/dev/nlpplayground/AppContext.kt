package dev.nlpplayground

import dev.nlpplayground.messaging.RabbitConnection
import dev.nlpplayground.persistence.PlaygroundDatabase
import dev.nlpplayground.pipeline.PipelineService
import dev.nlpplayground.session.SessionEvictionScheduler
import dev.nlpplayground.session.SessionStore
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
 *   be retired in Fase 1 once the SQLite-backed `TrainingRepository` lands.
 */
internal class AppContext(
    val config: Config = Config.fromEnv(),
    databaseFactory: (Config) -> PlaygroundDatabase = ::PlaygroundDatabase,
    storageFactory: (Config) -> MinioBlobStorage = ::MinioBlobStorage,
    rabbitFactory: (Config) -> RabbitConnection = ::RabbitConnection,
    val sessions: SessionStore = SessionStore(),
    val pipelineService: PipelineService = PipelineService(),
) {

    val database: PlaygroundDatabase = databaseFactory(config)
    val storage: MinioBlobStorage = storageFactory(config)
    val rabbit: RabbitConnection = rabbitFactory(config)
    val scheduler: SessionEvictionScheduler = SessionEvictionScheduler(sessions)

    fun close() {
        runCatching { rabbit.close() }
        runCatching { scheduler.stop() }
        // PlaygroundDatabase uses Exposed's static handle — Exposed cleans up via JVM shutdown hooks.
        // MinIO client is HTTP-based and stateless; nothing to close.
    }
}
