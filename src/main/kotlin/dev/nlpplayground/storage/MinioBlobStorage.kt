package dev.nlpplayground.storage

import dev.nlpplayground.Config
import io.minio.BucketExistsArgs
import io.minio.MinioClient
import org.slf4j.LoggerFactory

/**
 * Thin S3-compatible blob client. Only the connection + bucket-existence
 * checks live here for Fase 0 — actual upload/download wiring lands in
 * Fase 2 once `BlobStorage` interface is introduced.
 *
 * Buckets are created by the `minio-init` compose service (PRD §4.2) so the
 * app never tries to provision storage on its own.
 */
internal open class MinioBlobStorage(private val config: Config) {

    private val log = LoggerFactory.getLogger(MinioBlobStorage::class.java)

    // `MinioClient.builder().build()` is a pure configuration call — it does NOT
    // open a TCP connection. The first actual round-trip happens on `bucketExists`
    // or similar; tests can subclass and override `isHealthy()` to skip that.
    open val client: MinioClient = MinioClient.builder()
        .endpoint(config.minioEndpoint)
        .credentials(config.minioAccessKey, config.minioSecretKey)
        .build()

    open fun isHealthy(): Boolean = runCatching {
        bucketExists(config.corpusBucket) && bucketExists(config.modelsBucket)
    }.onFailure { e -> log.warn("MinIO health check failed", e) }.getOrDefault(false)

    private fun bucketExists(name: String): Boolean =
        client.bucketExists(BucketExistsArgs.builder().bucket(name).build())
}
