package dev.nlpplayground.pipeline

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe

class SemanticSearchTest :
    StringSpec({

        // Train once and reuse — Trainer is the slow part of these tests.
        val pipeline = CorpusTrainer.train(
            name = "tiny",
            corpus = """
                Alice was beginning to get very tired of sitting by her sister on the bank.
                She had peeped into the book her sister was reading.
                But it had no pictures or conversations in it, and what is the use of a book?
                So she was considering, in her own mind, whether the pleasure of making a daisy-chain
                would be worth the trouble of getting up and picking the daisies.
                When suddenly a White Rabbit with pink eyes ran close by her.
                Curiouser and curiouser, said Alice.
                Down the rabbit hole she went, head first, plunging into wonderland.
            """.trimIndent(),
            numMerges = 150,
        )

        "search returns at most topK results, ordered descending by score" {
            val results = SemanticSearch.search(pipeline, query = "alice went into wonderland", topK = 3)
            results shouldHaveSize 3
            val scores = results.map { it.score }
            scores shouldBe scores.sortedDescending()
        }

        "every returned score is a finite value in [-1, 1]" {
            val results = SemanticSearch.search(pipeline, query = "rabbit hole", topK = 5)
            results.forEach { r ->
                r.score.isFinite() shouldBe true
                r.score shouldBeGreaterThanOrEqualTo -1f
                r.score shouldBeLessThanOrEqualTo 1f
            }
        }

        "similarity is symmetric in its arguments" {
            val a = SemanticSearch.similarity(pipeline, "alice", "wonderland")
            val b = SemanticSearch.similarity(pipeline, "wonderland", "alice")
            a shouldBe b
        }

        "tokenize returns one entry per token id" {
            val view = SemanticSearch.tokenize(pipeline, "Alice")
            // 'A' 'l' 'i' 'c' 'e' as bytes minimum (5); merges may combine into fewer tokens.
            (view.size in 1..5) shouldBe true
            view.forEach { (it.id >= 0) shouldBe true }
        }

        "blank query is rejected" {
            val ex = runCatching { SemanticSearch.search(pipeline, query = "   ", topK = 3) }
                .exceptionOrNull()
            (ex is IllegalArgumentException) shouldBe true
        }
    })
