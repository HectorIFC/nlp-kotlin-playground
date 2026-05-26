package dev.nlpplayground.training

import dev.mosaic.EmbeddingTable
import dev.nlpplayground.Config
import dev.nlpplayground.pipeline.Pipeline
import dev.nlpplayground.pipeline.PretrainedLoader
import dev.nlpplayground.storage.BlobStorage
import dev.tessera.BpeTokenizer
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val CACHE_CAPACITY = 16

/** Sentinel prefix used in `corpus_blob_key` for bundled pretrained corpora. */
internal const val BUNDLED_PREFIX = "bundled:"

/**
 * Loads READY trainings' [Pipeline]s from their backing store (classpath for
 * bundled pretrained corpora, MinIO for everything else) and caches them in
 * an LRU map. Fetching the `tessera.json` + `mosaic.bin` + `corpus.txt`
 * triplet for every search request would be ~1 MB of HTTP traffic per query;
 * the same Pipeline serves arbitrarily many requests.
 *
 * Eviction: simple `LinkedHashMap` with `accessOrder=true`; capped at
 * [CACHE_CAPACITY] entries. The cache only ever holds READY pipelines; if a
 * training decays to EXPIRED, the route layer surfaces that without
 * involving the cache.
 */
internal class TrainingPipelineLoader(
    private val config: Config,
    private val storage: BlobStorage,
    private val pretrained: PretrainedLoader = PretrainedLoader.bundled(),
) {

    private val log = LoggerFactory.getLogger(TrainingPipelineLoader::class.java)

    @Suppress("MagicNumber")
    private val cache: MutableMap<String, Pipeline> = object : LinkedHashMap<String, Pipeline>(
        CACHE_CAPACITY,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pipeline>): Boolean =
            size > CACHE_CAPACITY
    }

    /**
     * Resolve a Pipeline for [training]. Bundled corpora load from the JAR
     * classpath via [PretrainedLoader]; everything else streams from MinIO.
     */
    @Synchronized
    fun resolve(training: Training): Pipeline {
        val key = training.id
        cache[key]?.let { return it }
        val pipeline = if (training.corpusBlobKey?.startsWith(BUNDLED_PREFIX) == true) {
            val name = training.corpusBlobKey.removePrefix(BUNDLED_PREFIX)
            log.debug("Loading bundled pretrained corpus '{}' for training {}", name, key)
            pretrained.load(name).copy(name = key)
        } else {
            log.debug("Cache miss — downloading model for training {} from MinIO", key)
            downloadFromMinio(key)
        }
        cache[key] = pipeline
        return pipeline
    }

    /** Available bundled corpus names (passthrough from [PretrainedLoader]). */
    fun bundled(): List<String> = pretrained.list()

    private fun downloadFromMinio(trainingId: String): Pipeline {
        val workDir = Files.createTempDirectory("playground-pipeline-$trainingId-")
        return try {
            val tess = stage(workDir, "tessera.json", "$trainingId/tessera.json")
            val mos = stage(workDir, "mosaic.bin", "$trainingId/mosaic.bin")
            // Mosaic verifies the .bin against this sidecar's SHA-256 checksum
            // on load — the consumer uploads it alongside the binary.
            stage(workDir, "mosaic.bin.meta.json", "$trainingId/mosaic.bin.meta.json")
            val sentencesPath = stage(workDir, "corpus.txt", "$trainingId/corpus.txt")
            Pipeline(
                name = trainingId,
                tokenizer = BpeTokenizer.load(tess.toString()),
                embeddings = EmbeddingTable.load(mos.toString()),
                sentences = Files.readAllLines(sentencesPath, Charsets.UTF_8).filter { it.isNotBlank() },
            )
        } finally {
            runCatching { workDir.toFile().deleteRecursively() }
        }
    }

    private fun stage(workDir: Path, fileName: String, blobKey: String): Path {
        val out = workDir.resolve(fileName)
        storage.openStream(config.modelsBucket, blobKey).use { input ->
            Files.copy(input, out, StandardCopyOption.REPLACE_EXISTING)
        }
        return out
    }
}
