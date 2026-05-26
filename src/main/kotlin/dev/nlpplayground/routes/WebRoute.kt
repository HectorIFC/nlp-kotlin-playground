package dev.nlpplayground.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * HTML routes that need a dynamic path segment, so the static handler can't
 * cover them. The frontend JS reads the id out of `window.location` — we just
 * serve the same HTML body regardless of which id is in the URL.
 *
 * Fase 4 will land richer progress + dashboard pages; for now we serve a
 * minimal placeholder so the upload flow has somewhere to redirect to.
 */
internal fun Route.webRoutes() {
    get("/explore/{sessionId}") {
        call.serveStatic("/static/explore.html")
    }

    get("/training/{id}/progress") {
        call.serveStatic("/static/training/progress.html", fallback = PROGRESS_PLACEHOLDER)
    }
}

private suspend fun ApplicationCall.serveStatic(path: String, fallback: String? = null) {
    val resource = WebRouteMarker::class.java.getResource(path)
    if (resource != null) {
        respondBytes(resource.readBytes(), contentType = ContentType.Text.Html)
        return
    }
    if (fallback != null) {
        respondText(fallback, ContentType.Text.Html)
        return
    }
    respondText("$path missing from classpath.", status = HttpStatusCode.InternalServerError)
}

private class WebRouteMarker

// Minimal placeholder served before Fase 4 ships the real progress UI.
private val PROGRESS_PLACEHOLDER = """
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>Training progress</title>
    <link rel="stylesheet" href="/css/main.css">
    <meta http-equiv="refresh" content="2">
</head>
<body>
    <main class="container" style="padding:48px 0">
        <h1>Training in progress…</h1>
        <p class="muted">
            The dashboard UI is being built in Fase 4. For now, poll
            <code>GET /api/training/{id}</code> directly to follow status,
            or refresh this page every couple of seconds.
        </p>
        <p><a href="/">← Back to home</a></p>
    </main>
</body>
</html>
""".trimIndent()
