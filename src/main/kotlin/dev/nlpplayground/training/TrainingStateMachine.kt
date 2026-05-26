package dev.nlpplayground.training

/**
 * Allowed [TrainingStatus] transitions. Anything not listed throws
 * `IllegalArgumentException` from [assertValidTransition] — the repository
 * enforces this on every status update so corrupted state can't sneak in
 * via a stray write.
 *
 * The forward chain is linear (QUEUED → DOWNLOADING → … → READY) with a
 * shortcut to FAILED from any non-terminal step. READY decays to EXPIRED
 * via a background sweeper; FAILED and EXPIRED never move again.
 *
 * See PRD §4.4 for the rationale and PRD §6.12 for the EXPIRED-from-READY-only
 * invariant (we never mark in-progress trainings as EXPIRED to avoid racing
 * with the consumer).
 */
internal object TrainingStateMachine {

    private val transitions: Map<TrainingStatus, Set<TrainingStatus>> = mapOf(
        TrainingStatus.QUEUED to setOf(TrainingStatus.DOWNLOADING, TrainingStatus.FAILED),
        TrainingStatus.DOWNLOADING to setOf(TrainingStatus.TOKENIZING, TrainingStatus.FAILED),
        TrainingStatus.TOKENIZING to setOf(TrainingStatus.EMBEDDING, TrainingStatus.FAILED),
        TrainingStatus.EMBEDDING to setOf(TrainingStatus.INDEXING, TrainingStatus.FAILED),
        TrainingStatus.INDEXING to setOf(TrainingStatus.READY, TrainingStatus.FAILED),
        TrainingStatus.READY to setOf(TrainingStatus.EXPIRED),
        // Terminal states — no outgoing transitions allowed.
        TrainingStatus.FAILED to emptySet(),
        TrainingStatus.EXPIRED to emptySet(),
    )

    fun assertValidTransition(from: TrainingStatus, to: TrainingStatus) {
        val allowed = transitions[from].orEmpty()
        require(to in allowed) {
            "Invalid transition: $from → $to. Allowed from $from: $allowed"
        }
    }

    fun allowedFrom(state: TrainingStatus): Set<TrainingStatus> = transitions[state].orEmpty()
}
