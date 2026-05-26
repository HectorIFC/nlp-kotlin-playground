package dev.nlpplayground.storage

import java.io.InputStream
import java.io.OutputStream

/**
 * Object storage abstraction. The MinIO backend is the only production impl;
 * tests substitute an in-memory implementation that doesn't need a running
 * container. PRD §1.4 #1.
 *
 * Bucket lifecycle (creation, ILM rules) is handled by the `minio-init`
 * compose service, not here — this interface only deals with object-level
 * operations on already-existing buckets.
 */
internal interface BlobStorage {

    /** Whether the configured buckets are reachable. Used by `/health`. */
    fun isHealthy(): Boolean

    /**
     * Upload [bytes] into [bucket] under [key]. Overwrites any existing
     * object with the same key (MinIO put-object semantics).
     */
    fun upload(bucket: String, key: String, bytes: ByteArray, contentType: String = "application/octet-stream")

    /** Streams the object at `<bucket>/<key>` into [sink]. Throws if the object is missing. */
    fun download(bucket: String, key: String, sink: OutputStream)

    /** Opens an [InputStream] for the object. Caller owns closing it. */
    fun openStream(bucket: String, key: String): InputStream

    /** Best-effort delete; does not raise if the object is already gone. */
    fun delete(bucket: String, key: String)
}
