package dev.nlpplayground

import dev.nlpplayground.messaging.RabbitConnection
import dev.nlpplayground.messaging.TrainingMessage
import dev.nlpplayground.messaging.TrainingPublisher
import dev.nlpplayground.persistence.PlaygroundDatabase
import dev.nlpplayground.storage.BlobStorage
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Test doubles for the infrastructure dependencies of [AppContext]. Each
 * subclass reports `isHealthy() = true` and short-circuits any real I/O,
 * so route tests can spin up the app without running MinIO/RabbitMQ.
 *
 * SQLite is exercised for real (via on-disk temp files / `:memory:`) because
 * Exposed needs an actual JDBC backend — the schema overhead is negligible.
 */
internal class StubPlaygroundDatabase(config: Config) : PlaygroundDatabase(config) {
    override fun isHealthy(): Boolean = true
}

/**
 * In-memory [BlobStorage] backed by a `ConcurrentHashMap`. Useful for upload-route
 * tests that want to assert "we wrote something" without spinning up MinIO.
 */
internal class InMemoryBlobStorage : BlobStorage {

    val objects = ConcurrentHashMap<String, ByteArray>()

    override fun isHealthy(): Boolean = true

    override fun upload(bucket: String, key: String, bytes: ByteArray, contentType: String) {
        objects["$bucket/$key"] = bytes
    }

    override fun download(bucket: String, key: String, sink: OutputStream) {
        val bytes = objects["$bucket/$key"] ?: error("No object at $bucket/$key")
        sink.write(bytes)
    }

    override fun openStream(bucket: String, key: String): InputStream {
        val bytes = objects["$bucket/$key"] ?: error("No object at $bucket/$key")
        return ByteArrayInputStream(bytes)
    }

    override fun delete(bucket: String, key: String) {
        objects.remove("$bucket/$key")
    }
}

internal class StubRabbitConnection(config: Config) : RabbitConnection(config) {
    override fun isHealthy(): Boolean = true
    override fun close() = Unit
}

/**
 * Stub publisher that records every message it sees instead of publishing.
 * Tests assert on `published` to confirm the upload route enqueued the right
 * payload without booting AMQP.
 */
internal class StubTrainingPublisher : TrainingPublisher(StubRabbitConnection(Config.fromEnv { null })) {
    val published = mutableListOf<TrainingMessage>()
    override fun publish(message: TrainingMessage) {
        published.add(message)
    }
}

/**
 * Each call returns a fresh `Config` pointing at a unique temp SQLite file.
 * Plain `:memory:` doesn't survive across Exposed-opened connections (each new
 * JDBC connection to an in-memory SQLite gets its own database), so the
 * schema created in `PlaygroundDatabase.init` would vanish before the first
 * `transaction { ... }` from a route handler. A per-test temp file gives us
 * the same isolation without the cross-connection problem.
 */
internal fun testConfig(): Config {
    val tmp = java.io.File.createTempFile("playground-test-", ".db").also { it.deleteOnExit() }
    return Config.fromEnv { null }.copy(sqlitePath = tmp.absolutePath)
}

/** Convenience overload — most tests don't care about the exact publisher / storage stubs. */
internal fun testAppContext(
    config: Config = testConfig(),
    storage: BlobStorage = InMemoryBlobStorage(),
    publisher: TrainingPublisher = StubTrainingPublisher(),
): AppContext = AppContext(
    config = config,
    databaseFactory = ::StubPlaygroundDatabase,
    storageFactory = { storage },
    rabbitFactory = ::StubRabbitConnection,
    publisherFactory = { publisher },
)
