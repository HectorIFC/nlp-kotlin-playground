package dev.nlpplayground.persistence

import dev.nlpplayground.Config
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.DriverManager

/**
 * SQLite connection holder. One [Database] instance per JVM; Exposed requires
 * `Database.connect` to be called once before any `transaction { ... }` block
 * (PRD §6.4).
 *
 * WAL mode (PRD §6.3) lets the producer keep reading while a consumer worker
 * is writing — otherwise concurrent updates hit `SQLITE_BUSY`. Schema creation
 * is deferred to Fase 1 (when `Trainings` and `TrainingEvents` tables land).
 */
internal open class PlaygroundDatabase(private val config: Config) {

    private val log = LoggerFactory.getLogger(PlaygroundDatabase::class.java)
    val handle: Database

    init {
        ensureParentDir(config.sqlitePath)
        // SQLite refuses `PRAGMA journal_mode=WAL` while inside an active
        // transaction (Exposed wraps every `exec` in BEGIN/COMMIT). Run the
        // PRAGMAs through a raw JDBC connection with autoCommit BEFORE Exposed
        // takes over — WAL mode is persisted in the SQLite file header, so the
        // Exposed-managed connections that come later pick it up automatically.
        // `:memory:` databases don't support WAL — skip the PRAGMA block there.
        if (config.sqlitePath != ":memory:") {
            DriverManager.getConnection("jdbc:sqlite:${config.sqlitePath}").use { conn ->
                conn.autoCommit = true
                conn.createStatement().use { stmt ->
                    stmt.execute("PRAGMA journal_mode=WAL;")
                    stmt.execute("PRAGMA foreign_keys=ON;")
                    stmt.execute("PRAGMA busy_timeout=5000;")
                }
            }
        }
        handle = Database.connect(
            url = "jdbc:sqlite:${config.sqlitePath}",
            driver = "org.sqlite.JDBC",
        )
        log.info(
            "SQLite connected at {} (WAL: {})",
            config.sqlitePath,
            config.sqlitePath != ":memory:",
        )
    }

    open fun isHealthy(): Boolean = runCatching {
        transaction(handle) {
            exec("SELECT 1;", explicitStatementType = StatementType.SELECT)
        }
        true
    }.getOrDefault(false)

    private fun ensureParentDir(path: String) {
        if (path == ":memory:") return
        val parent = File(path).parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
    }
}
