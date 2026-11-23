package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome

/**
 * U13 — outcome of [SaveToSpoolmanUseCase]. Save is the Spoolman-only half of
 * the old `Save & Write` button: it commits vendor/filament/spool records and
 * any expander-driven patches, but does not touch NFC. NFC pairing rides
 * [CreateAndPairUseCase] separately on a subsequent Write tap.
 */
sealed interface SaveToSpoolmanResult {

    sealed interface Success : SaveToSpoolmanResult {
        val spoolId: Int

        /** A spool was created or patched. [isNewSpool] tells the snackbar copy
         *  apart ("Saved spool #N." vs "Updated spool #N."). */
        data class Saved(
            override val spoolId: Int,
            val isNewSpool: Boolean,
        ) : Success
    }

    /** Existing-spool path where nothing differed from Spoolman, so no HTTP
     *  write was made (U32 B2). Shown to the user as "No changes to save". */
    data class NoChanges(val spoolId: Int) : SaveToSpoolmanResult

    data class Failed(
        val outcome: SpoolmanOutcome<*>,
    ) : SaveToSpoolmanResult

    data object UrlNotConfigured : SaveToSpoolmanResult
}
