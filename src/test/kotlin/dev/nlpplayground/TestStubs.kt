package dev.nlpplayground

import dev.nlpplayground.messaging.RabbitConnection
import dev.nlpplayground.persistence.PlaygroundDatabase
import dev.nlpplayground.storage.MinioBlobStorage

/**
 * Test doubles for the infrastructure dependencies of [AppContext]. Each
 * subclass reports `isHealthy() = true` without performing any I/O, so route
 * tests can spin up the app without a running MinIO/RabbitMQ/SQLite.
 *
 * Fase 1+ may replace these with Testcontainers-backed reals as the SQLite
 * + RabbitMQ + MinIO surfaces grow.
 */
internal class StubPlaygroundDatabase(config: Config) : PlaygroundDatabase(config) {
    override fun isHealthy(): Boolean = true
}

internal class StubMinioBlobStorage(config: Config) : MinioBlobStorage(config) {
    override fun isHealthy(): Boolean = true
}

internal class StubRabbitConnection(config: Config) : RabbitConnection(config) {
    override fun isHealthy(): Boolean = true
    override fun close() = Unit
}

/**
 * Build an in-memory [Config] suitable for tests: SQLite goes to `:memory:`
 * so the stub database stays in-process, and creds for MinIO/RabbitMQ are
 * present but irrelevant (stubs short-circuit before hitting them).
 */
internal fun testConfig(): Config = Config.fromEnv { null }.copy(sqlitePath = ":memory:")

/** Construct an [AppContext] wired entirely with test stubs. */
internal fun testAppContext(config: Config = testConfig()): AppContext = AppContext(
    config = config,
    databaseFactory = ::StubPlaygroundDatabase,
    storageFactory = ::StubMinioBlobStorage,
    rabbitFactory = ::StubRabbitConnection,
)
