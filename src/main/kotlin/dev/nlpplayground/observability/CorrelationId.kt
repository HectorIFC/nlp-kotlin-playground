package dev.nlpplayground.observability

import dev.nlpplayground.training.MDC_KEY
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.RouteScopedPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.hooks.CallSetup
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.util.AttributeKey
import org.slf4j.MDC

/**
 * Ktor route-scoped plugin that copies the `{trainingId}` (or `{id}`) path
 * parameter into the SLF4J [MDC] before the handler runs and removes it
 * after the call completes — either successfully ([ResponseSent]) or with
 * an exception ([CallFailed]). Without the `CallFailed` cleanup, a thread
 * pool could carry stale `training_id` values into the next unrelated
 * request that happens to land on it.
 *
 * Caveat: SLF4J's MDC is thread-local. Ktor's coroutine dispatching can
 * resume a handler on a different thread mid-suspend, at which point logs
 * lose the MDC tag. Our route handlers run inline on the IO dispatcher and
 * never explicitly switch context, so in practice the tag is observable on
 * every log line emitted from the handler body. If we ever introduce
 * `withContext` calls inside handlers, we'll need to pair this plugin with
 * `kotlinx-coroutines-slf4j`'s `MDCContext()`.
 *
 * The consumer-side training_id MDC is set directly by `TrainingService`
 * (on a worker thread, no coroutine dispatching) so it stays correct.
 */
internal val CorrelationId: RouteScopedPlugin<Unit> = createRouteScopedPlugin("CorrelationId") {
    val attrKey = AttributeKey<String>("playgroundTrainingId")

    on(CallSetup) { call ->
        val id = call.trainingIdParam() ?: return@on
        call.attributes.put(attrKey, id)
        MDC.put(MDC_KEY, id)
    }

    on(ResponseSent) { call ->
        if (call.attributes.contains(attrKey)) MDC.remove(MDC_KEY)
    }

    on(CallFailed) { call, _ ->
        if (call.attributes.contains(attrKey)) MDC.remove(MDC_KEY)
    }
}

private fun ApplicationCall.trainingIdParam(): String? = parameters["trainingId"] ?: parameters["id"]
