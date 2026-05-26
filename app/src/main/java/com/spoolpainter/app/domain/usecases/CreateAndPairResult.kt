package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.domain.primitives.CardUid

sealed interface CreateAndPairResult {

    sealed interface Success : CreateAndPairResult {
        val spoolId: Int
        val uid: CardUid

        data class WrittenAndPaired(
            override val spoolId: Int,
            override val uid: CardUid,
            val isNewSpool: Boolean,
        ) : Success
    }

    data class VerifyFailed(
        val spoolId: Int,
        val uid: CardUid,
        val isNewSpool: Boolean,
        val cause: String,
    ) : CreateAndPairResult

    data class SpoolmanFailed(
        val uid: CardUid,
        val outcome: SpoolmanOutcome<*>,
    ) : CreateAndPairResult

    data class NfcFailed(
        val uid: CardUid?,
        val reason: String,
    ) : CreateAndPairResult

    data class Cancelled(val reason: String) : CreateAndPairResult
}
