package dev.nlpplayground.pipeline

import dev.mosaic.EmbeddingTable
import dev.tessera.BpeTokenizer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Loads pre-trained corpora bundled inside the JAR under
 * `resources/pretrained/<name>/`. Each entry contains:
 *
 * - `tessera.json`        — serialized [BpeTokenizer]
 * - `mosaic.bin`          — serialized [EmbeddingTable]
 * - `mosaic.bin.meta.json` — embeddings metadata (consumed by Mosaic's persistence layer)
 * - `corpus.txt`          — UTF-8 source text that semantic search indexes against
 *
 * Tessera and Mosaic only expose file-based load APIs, so each resource is
 * copied to a temporary directory before being handed to the libraries. The
 * temp files are deleted as soon as loading completes; the in-memory pipeline
 * holds the parsed structures.
 *
 * Until Phase 4 commits the actual pre-trained artifacts, [list] returns an
 * empty list and [load] always throws.
 */
internal class PretrainedLoader(
    /**
     * Names of pre-trained corpora available on the classpath. Hard-coded to
     * what Phase 4 ships; falsely listing one without resources surfaces as
     * a `NoSuchPretrainedException` at load time.
     */
    val available: List<String> = emptyList(),
) {

    internal companion object {
        /** Names that match the directories committed under `resources/pretrained/`. */
        val BUNDLED_NAMES: List<String> = listOf(
            "alice-in-wonderland",
            "shakespeare-sonnets",
            "kotlin-stdlib-docs",
        )

        fun bundled(): PretrainedLoader = PretrainedLoader(available = BUNDLED_NAMES)
    }

    fun list(): List<String> = available

    fun load(name: String): Pipeline {
        require(name in available) {
            "Unknown pre-trained corpus: '$name'. Available: $available"
        }
        val tmpDir = Files.createTempDirectory("nlp-playground-pretrained-")
        try {
            val tokenizerFile = extractResource(name, "tessera.json", tmpDir)
            val embeddingsFile = extractResource(name, "mosaic.bin", tmpDir)
            extractResource(name, "mosaic.bin.meta.json", tmpDir)

            val tokenizer = BpeTokenizer.load(tokenizerFile.toString())
            val embeddings = EmbeddingTable.load(embeddingsFile.toString())
            val sentences = readCorpusLines(name)

            return Pipeline(
                name = name,
                tokenizer = tokenizer,
                embeddings = embeddings,
                sentences = sentences,
            )
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    private fun extractResource(corpus: String, fileName: String, targetDir: Path): Path {
        val resourcePath = "/pretrained/$corpus/$fileName"
        val input = javaClass.getResourceAsStream(resourcePath)
            ?: throw NoSuchPretrainedException("Missing resource: $resourcePath")
        val target = targetDir.resolve(fileName)
        input.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
        return target
    }

    private fun readCorpusLines(corpus: String): List<String> {
        val resourcePath = "/pretrained/$corpus/corpus.txt"
        val input = javaClass.getResourceAsStream(resourcePath)
            ?: throw NoSuchPretrainedException("Missing resource: $resourcePath")
        return input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }
}

internal class NoSuchPretrainedException(message: String) : IOException(message)
