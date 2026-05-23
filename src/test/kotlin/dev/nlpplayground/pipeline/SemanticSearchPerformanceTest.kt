package dev.nlpplayground.pipeline

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeLessThan

/**
 * Smoke check for perf target: search over a ~200-sentence corpus
 * should return in under 500 ms locally. Not a benchmark — just a regression net
 * that catches obvious algorithmic regressions (e.g., O(n²) or missing memoization).
 *
 * Shared CI runners can be hundreds of milliseconds slower than developer
 * laptops, so the upper bound here is intentionally generous. The local target
 * of < 500 ms is verified in the README and via manual measurement; tightening
 * this assertion would only buy flaky CI.
 */
private const val PERF_BUDGET_MS = 2_000L

class SemanticSearchPerformanceTest :
    StringSpec({

        "search over ~200 sentences completes well under the perf budget" {
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
            elapsedMs shouldBeLessThan PERF_BUDGET_MS
        }
    })
