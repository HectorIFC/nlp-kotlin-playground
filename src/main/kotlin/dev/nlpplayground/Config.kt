package dev.nlpplayground

/**
 * Runtime configuration loaded from environment variables. Defaults match the
 * `docker-compose.yml` topology so the app boots cleanly in dev without any
 * env tweaks.
 *
 * Each field maps 1:1 to a variable. Kept as a plain data class
 * (no DI framework) — wired manually in [AppContext].
 */
internal data class Config(
    // --- MinIO (blob storage) ---
    val minioEndpoint: String,
    val minioAccessKey: String,
    val minioSecretKey: String,
    val corpusBucket: String,
    val modelsBucket: String,

    // --- RabbitMQ (queue) ---
    val rabbitHost: String,
    val rabbitPort: Int,
    val rabbitUser: String,
    val rabbitPass: String,

    // --- SQLite (state persistence) ---
    val sqlitePath: String,

    // --- Consumer pool ---
    val consumerConcurrency: Int,

    // --- Upload + lifecycle ---
    val maxCorpusSizeBytes: Long,
    val trainingTtlHours: Long,
) {

    internal companion object {
        // default values; each can be overridden via env.
        private const val DEFAULT_MINIO_ENDPOINT = "http://minio:9000"
        private const val DEFAULT_MINIO_ACCESS_KEY = "playground"
        private const val DEFAULT_MINIO_SECRET_KEY = "playground123"
        private const val DEFAULT_CORPUS_BUCKET = "corpus-uploads"
        private const val DEFAULT_MODELS_BUCKET = "trained-models"

        private const val DEFAULT_RABBIT_HOST = "rabbitmq"
        private const val DEFAULT_RABBIT_PORT = 5672
        private const val DEFAULT_RABBIT_USER = "guest"
        private const val DEFAULT_RABBIT_PASS = "guest"

        private const val DEFAULT_SQLITE_PATH = "/data/playground.db"
        private const val DEFAULT_CONSUMER_CONCURRENCY = 2
        private const val DEFAULT_MAX_CORPUS_SIZE_BYTES = 2L * 1024 * 1024
        private const val DEFAULT_TRAINING_TTL_HOURS = 24L

        fun fromEnv(env: (String) -> String? = System::getenv): Config = Config(
            minioEndpoint = env("MINIO_ENDPOINT") ?: DEFAULT_MINIO_ENDPOINT,
            minioAccessKey = env("MINIO_ACCESS_KEY") ?: DEFAULT_MINIO_ACCESS_KEY,
            minioSecretKey = env("MINIO_SECRET_KEY") ?: DEFAULT_MINIO_SECRET_KEY,
            corpusBucket = env("MINIO_CORPUS_BUCKET") ?: DEFAULT_CORPUS_BUCKET,
            modelsBucket = env("MINIO_MODELS_BUCKET") ?: DEFAULT_MODELS_BUCKET,
            rabbitHost = env("RABBITMQ_HOST") ?: DEFAULT_RABBIT_HOST,
            rabbitPort = env.parsePort("RABBITMQ_PORT", DEFAULT_RABBIT_PORT),
            rabbitUser = env("RABBITMQ_USER") ?: DEFAULT_RABBIT_USER,
            rabbitPass = env("RABBITMQ_PASS") ?: DEFAULT_RABBIT_PASS,
            sqlitePath = env("SQLITE_PATH") ?: DEFAULT_SQLITE_PATH,
            consumerConcurrency = env.parsePositiveInt("CONSUMER_CONCURRENCY", DEFAULT_CONSUMER_CONCURRENCY),
            maxCorpusSizeBytes = env.parsePositiveLong("MAX_CORPUS_SIZE_BYTES", DEFAULT_MAX_CORPUS_SIZE_BYTES),
            trainingTtlHours = env.parsePositiveLong("TRAINING_TTL_HOURS", DEFAULT_TRAINING_TTL_HOURS),
        )

        private fun ((String) -> String?).parsePort(name: String, default: Int): Int {
            val raw = invoke(name) ?: return default
            val parsed = raw.toIntOrNull()
                ?: error("Invalid $name='$raw' — expected an integer between 1 and 65535")
            require(parsed in 1..PORT_MAX) {
                "Invalid $name=$parsed — must be in 1..65535"
            }
            return parsed
        }

        private fun ((String) -> String?).parsePositiveInt(name: String, default: Int): Int {
            val raw = invoke(name) ?: return default
            val parsed = raw.toIntOrNull()
                ?: error("Invalid $name='$raw' — expected a positive integer")
            require(parsed > 0) { "Invalid $name=$parsed — must be positive" }
            return parsed
        }

        private fun ((String) -> String?).parsePositiveLong(name: String, default: Long): Long {
            val raw = invoke(name) ?: return default
            val parsed = raw.toLongOrNull()
                ?: error("Invalid $name='$raw' — expected a positive long")
            require(parsed > 0) { "Invalid $name=$parsed — must be positive" }
            return parsed
        }

        private const val PORT_MAX = 65_535
    }
}
