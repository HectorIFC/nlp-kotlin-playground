package dev.nlpplayground

import dev.nlpplayground.pipeline.PipelineService
import dev.nlpplayground.session.SessionEvictionScheduler
import dev.nlpplayground.session.SessionStore

/**
 * Wired-up runtime collaborators. One instance per JVM in production; tests
 * can build their own with stubs or hand-rolled fakes.
 *
 * Lifecycle:
 *
 * - [scheduler] is started on application start and stopped on shutdown.
 * - [sessions] and [pipelineService] are plain values; nothing to release.
 */
internal class AppContext(
    val sessions: SessionStore = SessionStore(),
    val pipelineService: PipelineService = PipelineService(),
    val scheduler: SessionEvictionScheduler = SessionEvictionScheduler(sessions),
)
