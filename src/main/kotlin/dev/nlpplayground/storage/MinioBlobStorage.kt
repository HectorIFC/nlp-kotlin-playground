package dev.nlpplayground.storage

import dev.nlpplayground.Config
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.errors.ErrorResponseException
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Production [BlobStorage] backed by MinIO. The `MinioClient.builder()...build()`
 * call is configuration-only — no TCP connection is opened until the first
 * upload/download/delete/bucketExists, which keeps construction cheap and
 * makes tests that don't exercise I/O fast.
 *
 * Buckets are created by the `minio-init` compose service (PRD §4.2); we only
 * verify their presence in [isHealthy].
 */
internal open class MinioBlobStorage(private val config: Config) : BlobStorage {

    private val log = LoggerFactory.getLogger(MinioBlobStorage::class.java)

    open val client: MinioClient = MinioClient.builder()
        .endpoint(config.minioEndpoint)
        .credentials(config.minioAccessKey, config.minioSecretKey)
        .build()

    override fun isHealthy(): Boolean = runCatching {
        bucketExists(config.corpusBucket) && bucketExists(config.modelsBucket)
    }.onFailure { e -> log.warn("MinIO health check failed", e) }.getOrDefault(false)

    override fun upload(bucket: String, key: String, bytes: ByteArray, contentType: String) {
        ByteArrayInputStream(bytes).use { stream ->
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(key)
                    .stream(stream, bytes.size.toLong(), -1)
                    .contentType(contentType)
                    .build(),
            )
        }
    }

    override fun download(bucket: String, key: String, sink: OutputStream) {
        openStream(bucket, key).use { it.copyTo(sink) }
    }

    override fun openStream(bucket: String, key: String): InputStream =
        client.getObject(GetObjectArgs.builder().bucket(bucket).`object`(key).build())

    override fun delete(bucket: String, key: String) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).`object`(key).build())
        } catch (e: ErrorResponseException) {
            // 404-equivalent "NoSuchKey" — treat as a no-op so callers can call delete idempotently.
            if (e.errorResponse().code() == "NoSuchKey") return
            throw e
        }
    }

    private fun bucketExists(name: String): Boolean =
        client.bucketExists(BucketExistsArgs.builder().bucket(name).build())
}
