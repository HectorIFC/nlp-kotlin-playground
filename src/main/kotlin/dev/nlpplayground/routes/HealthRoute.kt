package dev.nlpplayground.routes

import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.healthRoute() {
    get("/health") {
        call.respondText("ok")
    }
}
