package dev.nlpplayground.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.nlpplayground.Config
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.DriverManager

/**
 * SQLite connection holder. One [Database] instance per JVM; Exposed requires
 * `Database.connect` to be called once before any `transaction { ... }` block
 *
 * Two PRAGMA flavours and where each is applied:
 *
 * - `journal_mode=WAL` is a **file-level** SQLite setting — once
 *   set, it persists in the database header. We apply it once at startup via
 *   a raw JDBC connection (Exposed wraps every statement in BEGIN/COMMIT,
 *   and SQLite refuses `journal_mode=WAL` inside a transaction).
 * - `foreign_keys=ON` and `busy_timeout=5000` are **per-connection** PRAGMAs.
 *   They have to be applied to every JDBC connection Exposed acquires, which
 *   is why we route the runtime through HikariCP with `connectionInitSql`.
 *
 * `:memory:` databases don't support WAL — the PRAGMA block is skipped there.
 */
internal open class PlaygroundDatabase(private val config: Config) {

    private val log = LoggerFactory.getLogger(PlaygroundDatabase::class.java)
    val handle: Database
    private val dataSource: HikariDataSource?

    init {
        ensureParentDir(config.sqlitePath)
        val jdbcUrl = "jdbc:sqlite:${config.sqlitePath}"

        if (config.sqlitePath != ":memory:") {
            // One-time WAL switch (persisted in the SQLite file header).
            DriverManager.getConnection(jdbcUrl).use { conn ->
                conn.autoCommit = true
                conn.createStatement().use { it.execute("PRAGMA journal_mode=WAL;") }
            }
        }

        // HikariCP pool with init SQL ensures every Exposed-managed connection
        // gets the per-connection PRAGMAs we care about.
        dataSource = HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                driverClassName = "org.sqlite.JDBC"
                connectionInitSql = "PRAGMA foreign_keys=ON; PRAGMA busy_timeout=5000;"
                // SQLite serializes writes regardless; keep the pool small so
                // SQLITE_BUSY contention is rare. Two consumers + the producer
                // need a handful of connections at most.
                maximumPoolSize = MAX_POOL_SIZE
                poolName = "playground-sqlite"
            },
        )
        handle = Database.connect(dataSource)

        transaction(handle) {
            SchemaUtils.create(Trainings, TrainingEvents)
        }

        log.info(
            "SQLite connected at {} (WAL: {}, pool size: {})",
            config.sqlitePath,
            config.sqlitePath != ":memory:",
            MAX_POOL_SIZE,
        )
    }

    open fun isHealthy(): Boolean = runCatching {
        transaction(handle) {
            exec("SELECT 1;", explicitStatementType = StatementType.SELECT)
        }
        true
    }.getOrDefault(false)

    fun close() {
        dataSource?.close()
    }

    private fun ensureParentDir(path: String) {
        if (path == ":memory:") return
        val parent = File(path).parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
    }

    private companion object {
        const val MAX_POOL_SIZE = 6
    }
}
