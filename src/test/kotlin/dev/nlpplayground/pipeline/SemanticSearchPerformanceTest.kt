package dev.nlpplayground.pipeline

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeLessThan

/**
 * Smoke check for the PRD §3.1 perf target: search over a ~200-sentence corpus
 * should return in under 500 ms. Not a benchmark — just a regression net.
 */
class SemanticSearchPerformanceTest :
    StringSpec({

        "search over ~200 sentences completes well under 500ms" {
            val corpus = (1..210).joinToString("\n") { idx ->
                "Sentence number $idx talks about topic ${idx % 17} and theme ${idx % 13}."
            }
            val pipeline = CorpusTrainer.train(name = "perf", corpus = corpus, numMerges = 300)

            // Warm up the JIT once — first call also primes the Tessera/Mosaic internals.
            SemanticSearch.search(pipeline, query = "topic seven", topK = 5)

            val start = System.nanoTime()
            val results = SemanticSearch.search(pipeline, query = "talks about topic three", topK = 5)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000

            results.size shouldBeLessThan 6
            elapsedMs shouldBeLessThan 500
        }
    })
