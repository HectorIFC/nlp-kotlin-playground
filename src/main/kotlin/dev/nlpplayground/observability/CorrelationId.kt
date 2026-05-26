package dev.nlpplayground.observability

import dev.nlpplayground.training.MDC_KEY
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.RouteScopedPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.hooks.CallSetup
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.util.AttributeKey
import org.slf4j.MDC

/**
 * Ktor route-scoped plugin that copies the `{trainingId}` (or `{id}`) path
 * parameter into the SLF4J [MDC] before the handler runs and removes it
 * after the response is sent. Pairs with [Logback]'s `LogstashEncoder`,
 * which surfaces MDC values as JSON fields automatically.
 *
 * The consumer-side training_id MDC is set directly by `TrainingService`
 * because there's no HTTP call to hook into there.
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
}

private fun ApplicationCall.trainingIdParam(): String? = parameters["trainingId"] ?: parameters["id"]
