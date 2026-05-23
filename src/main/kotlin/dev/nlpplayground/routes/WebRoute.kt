package dev.nlpplayground.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Routes that serve the SPA-lite frontend. Static assets (CSS, JS, the home
 * page) are served via `staticResources` higher up in the routing tree; only
 * the dynamic-path `/explore/{sessionId}` route lives here because Ktor's
 * static handler doesn't match dynamic path segments.
 */
internal fun Route.webRoutes() {
    get("/explore/{sessionId}") {
        val sessionId = call.parameters["sessionId"]
        if (sessionId.isNullOrBlank()) {
            return@get call.respondText(
                "Missing sessionId.",
                status = HttpStatusCode.BadRequest,
            )
        }
        // The JS layer parses the sessionId out of window.location.pathname,
        // so we just serve the same HTML body for any valid {sessionId}.
        val resource = WebRoute::class.java.getResource("/static/explore.html")
            ?: return@get call.respondText(
                "explore.html missing from classpath.",
                status = HttpStatusCode.InternalServerError,
            )
        call.respondBytes(resource.readBytes(), contentType = ContentType.Text.Html)
    }
}

private class WebRoute
