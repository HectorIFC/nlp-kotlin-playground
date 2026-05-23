package dev.nlpplayground.pipeline

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class PretrainedLoaderTest :
    StringSpec({

        "default loader lists no pretrained corpora (resources land in phase 4)" {
            PretrainedLoader().list().shouldBeEmpty()
        }

        "loading an unknown name fails with a helpful message" {
            val loader = PretrainedLoader(available = listOf("alice-in-wonderland"))
            val ex = shouldThrow<IllegalArgumentException> { loader.load("shakespeare") }
            (ex.message?.contains("Unknown pre-trained corpus") == true) shouldBe true
        }

        "loading a listed-but-missing resource surfaces NoSuchPretrainedException" {
            // We declare a corpus that has no actual files on the classpath, so the
            // resource-extraction step is exercised end-to-end without bundled data.
            val loader = PretrainedLoader(available = listOf("ghost"))
            shouldThrow<NoSuchPretrainedException> { loader.load("ghost") }
        }
    })
