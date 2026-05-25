package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.TagClassification

sealed interface ReadAndPairResult {

    sealed interface Success : ReadAndPairResult {
        val uid: CardUid

        data class PrefillFromSpoolman(
            override val uid: CardUid,
            val spool: SpoolmanSpool,
            val classification: TagClassification,
        ) : Success

        data class PrefillFromTag(
            override val uid: CardUid,
            val payload: OpenSpoolPayload,
        ) : Success

        data class BlankForm(
            override val uid: CardUid,
            val classification: TagClassification,
        ) : Success
    }

    data class Ambiguous(
        val uid: CardUid,
        val matches: List<SpoolmanSpool>,
        val classification: TagClassification,
    ) : ReadAndPairResult

    data class SpoolmanFailed(
        val uid: CardUid,
        val classification: TagClassification,
        val outcome: SpoolmanOutcome<*>,
    ) : ReadAndPairResult

    data class NfcFailed(val reason: String) : ReadAndPairResult

    data class Cancelled(val reason: String) : ReadAndPairResult
}
