package dev.nlpplayground.pipeline

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

class PretrainedLoaderTest :
    StringSpec({

        "bundled() lists the three corpora shipped under resources/pretrained/" {
            PretrainedLoader.bundled().list() shouldContainExactly listOf(
                "alice-in-wonderland",
                "shakespeare-sonnets",
                "kotlin-stdlib-docs",
            )
        }

        "loading an unknown name fails with a helpful message" {
            val loader = PretrainedLoader(available = listOf("alice-in-wonderland"))
            val ex = shouldThrow<IllegalArgumentException> { loader.load("shakespeare") }
            (ex.message?.contains("Unknown pre-trained corpus") == true) shouldBe true
        }

        "loading a listed-but-missing resource surfaces NoSuchPretrainedException" {
            val loader = PretrainedLoader(available = listOf("ghost"))
            shouldThrow<NoSuchPretrainedException> { loader.load("ghost") }
        }

        "loading a bundled corpus parses tokenizer + embeddings + sentences" {
            val pipeline = PretrainedLoader.bundled().load("alice-in-wonderland")
            pipeline.name shouldBe "alice-in-wonderland"
            pipeline.tokenizer.vocabSize shouldBe pipeline.embeddings.vocabSize
            pipeline.embeddings.embeddingDim shouldBe 128
            pipeline.sentences.shouldNotBeEmpty()
        }
    })
