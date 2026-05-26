package dev.nlpplayground.training

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class TrainingStateMachineTest :
    StringSpec({

        "happy-path chain QUEUED → DOWNLOADING → ... → READY all pass" {
            val chain = listOf(
                TrainingStatus.QUEUED to TrainingStatus.DOWNLOADING,
                TrainingStatus.DOWNLOADING to TrainingStatus.TOKENIZING,
                TrainingStatus.TOKENIZING to TrainingStatus.EMBEDDING,
                TrainingStatus.EMBEDDING to TrainingStatus.INDEXING,
                TrainingStatus.INDEXING to TrainingStatus.READY,
                TrainingStatus.READY to TrainingStatus.EXPIRED,
            )
            for ((from, to) in chain) {
                TrainingStateMachine.assertValidTransition(from, to)
            }
        }

        "any non-terminal state can short-circuit to FAILED" {
            val sources = listOf(
                TrainingStatus.QUEUED,
                TrainingStatus.DOWNLOADING,
                TrainingStatus.TOKENIZING,
                TrainingStatus.EMBEDDING,
                TrainingStatus.INDEXING,
            )
            for (from in sources) {
                TrainingStateMachine.assertValidTransition(from, TrainingStatus.FAILED)
            }
        }

        "FAILED and EXPIRED are terminal — no outgoing transitions allowed" {
            TrainingStateMachine.allowedFrom(TrainingStatus.FAILED).isEmpty() shouldBe true
            TrainingStateMachine.allowedFrom(TrainingStatus.EXPIRED).isEmpty() shouldBe true

            shouldThrow<IllegalArgumentException> {
                TrainingStateMachine.assertValidTransition(TrainingStatus.FAILED, TrainingStatus.READY)
            }
            shouldThrow<IllegalArgumentException> {
                TrainingStateMachine.assertValidTransition(TrainingStatus.EXPIRED, TrainingStatus.QUEUED)
            }
        }

        "READY only transitions to EXPIRED, never back to in-progress states (PRD §6.12)" {
            TrainingStateMachine.allowedFrom(TrainingStatus.READY) shouldContainExactlyInAnyOrder
                setOf(TrainingStatus.EXPIRED)

            // Picking a few representative invalid targets — exhaustive
            // enumeration is covered indirectly by "skipping forward steps".
            listOf(TrainingStatus.QUEUED, TrainingStatus.DOWNLOADING, TrainingStatus.READY).forEach { invalid ->
                shouldThrow<IllegalArgumentException> {
                    TrainingStateMachine.assertValidTransition(TrainingStatus.READY, invalid)
                }
            }
        }

        "skipping forward steps in the pipeline is rejected" {
            // QUEUED → INDEXING is not allowed (must go through DOWNLOADING / TOKENIZING / EMBEDDING).
            shouldThrow<IllegalArgumentException> {
                TrainingStateMachine.assertValidTransition(TrainingStatus.QUEUED, TrainingStatus.INDEXING)
            }
            // DOWNLOADING → READY is not allowed either.
            shouldThrow<IllegalArgumentException> {
                TrainingStateMachine.assertValidTransition(TrainingStatus.DOWNLOADING, TrainingStatus.READY)
            }
        }

        "EXPIRED cannot be reached from non-READY states" {
            val nonReady = TrainingStatus.entries.filter { it != TrainingStatus.READY }
            nonReady.forEach { from ->
                TrainingStateMachine.allowedFrom(from).shouldNotContain(TrainingStatus.EXPIRED)
            }
        }
    })
