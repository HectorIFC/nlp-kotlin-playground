package dev.nlpplayground.pretrainer

import dev.mosaic.EmbeddingTable
import dev.mosaic.Initializer
import dev.tessera.Trainer
import dev.tessera.TrainingConfig
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Regenerates the three bundled pre-trained corpora into
 * `src/main/resources/pretrained/<name>/`. Each corpus produces:
 *
 * - corpus.txt              — cleaned, UTF-8 source text (one sentence per line ideally)
 * - tessera.json            — serialized BpeTokenizer
 * - mosaic.bin              — serialized EmbeddingTable
 * - mosaic.bin.meta.json    — Mosaic's metadata sidecar
 *
 * Downloaded raw text is cached under `.corpus-cache/` (gitignored). The
 * committed artifacts are deterministic given the inputs — same seed, same
 * merges, same cleaning steps.
 *
 * Usage: `./gradlew runPretrain [-PoutDir=...]`
 */
private const val NUM_MERGES = 2000
private const val EMBEDDING_DIM = 128
private const val EMBEDDING_SEED = 42L

fun main(args: Array<String>) {
    val outBase = File(args.getOrNull(0) ?: "src/main/resources/pretrained")
    val cacheDir = File(".corpus-cache").also { it.mkdirs() }

    val corpora = listOf(
        Corpus(
            name = "alice-in-wonderland",
            cleaner = GutenbergCleaner,
            sources = listOf("https://www.gutenberg.org/cache/epub/11/pg11.txt"),
        ),
        Corpus(
            name = "shakespeare-sonnets",
            cleaner = GutenbergCleaner,
            sources = listOf("https://www.gutenberg.org/cache/epub/1041/pg1041.txt"),
        ),
        Corpus(
            name = "kotlin-stdlib-docs",
            cleaner = KdocCleaner,
            sources = KOTLIN_STDLIB_SOURCES,
        ),
    )

    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
    for (corpus in corpora) {
        println("=== ${corpus.name} ===")
        process(corpus, cacheDir, outBase, client)
    }
    println("Done. Output under: ${outBase.absolutePath}")
}

private fun process(corpus: Corpus, cacheDir: File, outBase: File, client: HttpClient) {
    val rawParts = corpus.sources.mapIndexed { idx, url ->
        val cached = File(cacheDir, "${corpus.name}-$idx.raw")
        if (!cached.exists()) {
            println("  downloading $url …")
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "nlp-kotlin-playground/pretrainer")
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
            check(response.statusCode() == 200) { "Failed to fetch $url: HTTP ${response.statusCode()}" }
            cached.writeText(response.body(), Charsets.UTF_8)
            println("  cached ${cached.length()} bytes -> ${cached.name}")
        } else {
            println("  using cached ${cached.name} (${cached.length()} bytes)")
        }
        cached.readText(Charsets.UTF_8)
    }

    val cleaned = corpus.cleaner.clean(rawParts)
    require(cleaned.length > 5_000) {
        "Cleaned corpus for ${corpus.name} is suspiciously short: ${cleaned.length} chars"
    }
    println("  cleaned: ${cleaned.length} chars")

    val outDir = File(outBase, corpus.name).also { it.mkdirs() }
    File(outDir, "corpus.txt").writeText(cleaned, Charsets.UTF_8)
    println("  wrote corpus.txt")

    val tokenizer = Trainer(TrainingConfig(numMerges = NUM_MERGES, verbose = false)).train(cleaned)
    tokenizer.save(File(outDir, "tessera.json").absolutePath)
    println("  wrote tessera.json (vocab ${tokenizer.vocabSize})")

    val embeddings = EmbeddingTable.create(
        vocabSize = tokenizer.vocabSize,
        embeddingDim = EMBEDDING_DIM,
        initializer = Initializer.uniformDefault(seed = EMBEDDING_SEED),
    )
    embeddings.save(File(outDir, "mosaic.bin").absolutePath)
    println("  wrote mosaic.bin (${embeddings.vocabSize} × ${embeddings.embeddingDim})")
}

private data class Corpus(val name: String, val cleaner: Cleaner, val sources: List<String>)

private fun interface Cleaner {
    fun clean(parts: List<String>): String
}

/**
 * Strips Project Gutenberg license boilerplate. Each file has obvious
 * start/end markers — we keep only the bytes between them.
 */
private val GutenbergCleaner = Cleaner { parts ->
    parts.joinToString("\n\n") { strip(it) }.trim()
}

private fun strip(raw: String): String {
    val startMarker = Regex("""\*\*\* START OF THE PROJECT GUTENBERG EBOOK [^*]+ \*\*\*""")
    val endMarker = Regex("""\*\*\* END OF THE PROJECT GUTENBERG EBOOK [^*]+ \*\*\*""")
    val startMatch = startMarker.find(raw)
    val endMatch = endMarker.find(raw)
    val body = if (startMatch != null && endMatch != null && endMatch.range.first > startMatch.range.last) {
        raw.substring(startMatch.range.last + 1, endMatch.range.first)
    } else {
        raw
    }
    return body
        .lineSequence()
        .map { it.trimEnd() }
        .joinToString("\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}

/**
 * Extracts the contents of `/** ... */` KDoc blocks from raw Kotlin source,
 * one line per concatenated stripped block. Skips one-liner @param/@return
 * lines that don't add semantic content.
 */
private val KdocCleaner = Cleaner { parts ->
    val kdocPattern = Regex("""/\*\*(.+?)\*/""", RegexOption.DOT_MATCHES_ALL)
    val lineCleaner = Regex("""^\s*\*\s?""", RegexOption.MULTILINE)
    val tagSkipper = Regex("""^@(param|return|throws|see|since|sample)\b.*$""", RegexOption.MULTILINE)

    val blocks = parts.flatMap { raw ->
        kdocPattern.findAll(raw).map { match ->
            match.groupValues[1]
                .replace(lineCleaner, "")
                .replace(tagSkipper, "")
                .trim()
        }.filter { it.length > 20 }
    }
    blocks.joinToString("\n\n")
}

/**
 * Curated Kotlin stdlib source files whose KDocs are dense and self-contained.
 * Pinned to a tag so the corpus is reproducible across runs.
 */
private const val KOTLIN_TAG = "v2.0.21"
private const val KOTLIN_STDLIB_BASE =
    "https://raw.githubusercontent.com/JetBrains/kotlin/$KOTLIN_TAG/libraries/stdlib/src/kotlin"
private val KOTLIN_STDLIB_SOURCES = listOf(
    "$KOTLIN_STDLIB_BASE/collections/Collections.kt",
    "$KOTLIN_STDLIB_BASE/collections/Sequences.kt",
    "$KOTLIN_STDLIB_BASE/collections/Maps.kt",
    "$KOTLIN_STDLIB_BASE/collections/Sets.kt",
    "$KOTLIN_STDLIB_BASE/collections/Iterables.kt",
    "$KOTLIN_STDLIB_BASE/text/Strings.kt",
    "$KOTLIN_STDLIB_BASE/util/Lazy.kt",
)
