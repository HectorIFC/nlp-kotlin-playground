package dev.nlpplayground.pipeline

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.throwable.shouldHaveMessage

class CorpusTrainerTest :
    StringSpec({

        "training produces a vocab strictly larger than 256 base bytes" {
            val pipeline = CorpusTrainer.train(
                name = "tiny",
                corpus = TINY_CORPUS,
                numMerges = 50,
            )
            pipeline.tokenizer.vocabSize shouldBeGreaterThan 256
            pipeline.embeddings.vocabSize shouldBe pipeline.tokenizer.vocabSize
            pipeline.embeddings.embeddingDim shouldBe 128
        }

        "sentence split drops fragments shorter than the minimum length" {
            val pipeline = CorpusTrainer.train(
                name = "tiny",
                corpus = TINY_CORPUS,
                numMerges = 20,
            )
            pipeline.sentences.shouldNotBeEmpty()
            // Every retained sentence is above the trimmed length floor.
            pipeline.sentences.forEach { it.length shouldBeGreaterThan 10 }
        }

        "blank corpus is rejected with a clear message" {
            val ex = runCatching {
                CorpusTrainer.train(name = "blank", corpus = "   \n  ", numMerges = 10)
            }.exceptionOrNull()!!
            ex.shouldHaveMessage("Corpus must not be empty.")
        }

        "fewer requested merges keeps vocab close to the base size" {
            val pipeline = CorpusTrainer.train(name = "tiny", corpus = TINY_CORPUS, numMerges = 5)
            // 256 (bytes) + up to 5 merges + 1 special token (<|endoftext|>)
            pipeline.tokenizer.vocabSize shouldBeLessThanOrEqualTo 256 + 5 + 1
            pipeline.name shouldStartWith "tiny"
        }
    }) {
    companion object {
        // Small, varied UTF-8 corpus — enough to exercise merges without dominating test time.
        private val TINY_CORPUS = """
            The quick brown fox jumps over the lazy dog.
            A red fox runs through the deep forest at dusk.
            Curiosity led Alice down the rabbit hole into wonderland.
            Down, down, down — would the fall never come to an end?
            Lazy dogs sleep under warm summer sunsets.
            The forest is dark and full of small mysteries.
            Alice met a hookah-smoking caterpillar atop a giant mushroom.
        """.trimIndent()
    }
}
