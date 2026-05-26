package dev.nlpplayground

/**
 * Runtime configuration loaded from environment variables. Defaults match the
 * `docker-compose.yml` topology so the app boots cleanly in dev without any
 * env tweaks.
 *
 * Each field maps 1:1 to a variable in PRD §4.11. Kept as a plain data class
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
        // PRD §4.11 default values; each can be overridden via env.
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
            rabbitPort = env("RABBITMQ_PORT")?.toInt() ?: DEFAULT_RABBIT_PORT,
            rabbitUser = env("RABBITMQ_USER") ?: DEFAULT_RABBIT_USER,
            rabbitPass = env("RABBITMQ_PASS") ?: DEFAULT_RABBIT_PASS,
            sqlitePath = env("SQLITE_PATH") ?: DEFAULT_SQLITE_PATH,
            consumerConcurrency = env("CONSUMER_CONCURRENCY")?.toInt() ?: DEFAULT_CONSUMER_CONCURRENCY,
            maxCorpusSizeBytes = env("MAX_CORPUS_SIZE_BYTES")?.toLong() ?: DEFAULT_MAX_CORPUS_SIZE_BYTES,
            trainingTtlHours = env("TRAINING_TTL_HOURS")?.toLong() ?: DEFAULT_TRAINING_TTL_HOURS,
        )
    }
}
