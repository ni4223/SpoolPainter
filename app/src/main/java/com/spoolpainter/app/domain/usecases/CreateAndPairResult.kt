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
            /** True when the write step was skipped because the tap landed
             *  on a vendor-classified tag — Spoolman's UID mapping was still
             *  written, but no NDEF payload landed on the tag. */
            val isVendorPair: Boolean = false,
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
        /** Set when the spool was already created in Spoolman before the
         *  write tap failed. Lets the UI pin the selection so the next
         *  retry tap appends to this spool instead of creating a duplicate. */
        val spoolId: Int? = null,
    ) : CreateAndPairResult

    data class Cancelled(
        val reason: String,
        val spoolId: Int? = null,
    ) : CreateAndPairResult
}
