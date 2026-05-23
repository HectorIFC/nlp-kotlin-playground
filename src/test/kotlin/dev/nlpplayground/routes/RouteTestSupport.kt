package dev.nlpplayground.routes

import dev.nlpplayground.AppContext
import dev.nlpplayground.moduleWith
import dev.nlpplayground.pipeline.CorpusTrainer
import dev.nlpplayground.pipeline.Pipeline
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json

internal val testJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun ApplicationTestBuilder.testClient(): HttpClient = createClient {
    install(ContentNegotiation) { json(testJson) }
}

internal fun ApplicationTestBuilder.installApp(ctx: AppContext = AppContext()) {
    application { moduleWith(ctx) }
}

internal fun tinyPipeline(name: String = "tiny"): Pipeline = CorpusTrainer.train(
    name = name,
    corpus = """
            Alice was beginning to get very tired of sitting by her sister on the bank.
            She peeped into the book her sister was reading.
            Down the rabbit hole she went, head first into wonderland.
            The hookah-smoking caterpillar sat atop a giant mushroom.
            Curiouser and curiouser said the small girl with golden hair.
    """.trimIndent(),
    numMerges = 80,
)
