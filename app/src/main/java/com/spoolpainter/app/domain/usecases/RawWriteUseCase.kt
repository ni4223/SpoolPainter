package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.hardware.nfc.NfcRepository
import com.spoolpainter.app.ui.screens.main.FormState
import javax.inject.Inject

data class RawWriteInput(
    val form: FormState,
    val resolvedMaterialName: String? = null,
    val newFilamentVendor: String = "Unknown",
)

sealed interface RawWriteResult {
    sealed interface Success : RawWriteResult {
        data class Written(val uid: CardUid) : Success
    }
    data class VendorTagRejected(val uid: CardUid) : RawWriteResult
    data class VerifyFailed(val uid: CardUid, val cause: String) : RawWriteResult
    data class NfcFailed(val uid: CardUid?, val reason: String) : RawWriteResult
    data class Cancelled(val reason: String) : RawWriteResult
}

/**
 * Writes an OpenSpool payload to a blank or OpenSpool tag with **zero**
 * Spoolman interaction. Auto-engaged when [com.spoolpainter.app.ui.screens.main.WriteMode]
 * is `RawNoUrl` or `RawDisconnected`. Constructor injects only [NfcRepository]
 * — type-level invariant that this use-case never touches Spoolman.
 *
 * FR-4.7 vendor protection + FR-4.5 write-then-verify still enforced via
 * [NfcRepository.runWriteThenVerify].
 */
open class RawWriteUseCase @Inject constructor(
    protected val nfc: NfcRepository,
) {

    open suspend operator fun invoke(input: RawWriteInput): RawWriteResult {
        val payload = makePayload(input)
        nfc.arm(NfcIntent.Write(payload, expectedUid = null))
        val outcome = awaitTerminalNfc()
        if (outcome == null) {
            // No tap landed within the wait window — disarm so the stale
            // arm can't consume a late tap into the void.
            nfc.disarm()
            return RawWriteResult.Cancelled("timeout")
        }
        return when (outcome) {
            is NfcResult.Success ->
                if (outcome.uid.hex.isEmpty()) {
                    RawWriteResult.NfcFailed(null, "zero-length UID, non-NFC-A tag?")
                } else {
                    RawWriteResult.Success.Written(outcome.uid)
                }
            is NfcResult.Error -> {
                val reason = outcome.reason
                val lastUid = nfc.lastSeenTag.value?.uid
                when {
                    reason.contains("vendor-tag", ignoreCase = true) ->
                        RawWriteResult.VendorTagRejected(lastUid ?: CardUid(""))
                    reason.contains("verify mismatch", ignoreCase = true) ||
                        reason.contains("verification failed", ignoreCase = true) ->
                        RawWriteResult.VerifyFailed(lastUid ?: CardUid(""), reason)
                    else ->
                        RawWriteResult.NfcFailed(lastUid, reason)
                }
            }
            else -> RawWriteResult.NfcFailed(null, "unexpected write state: $outcome")
        }
    }

    private fun makePayload(input: RawWriteInput): OpenSpoolPayload {
        val form = input.form
        val material = input.resolvedMaterialName?.takeIf { it.isNotBlank() }
            ?: form.material?.name
            ?: "PLA"
        val brandName = input.newFilamentVendor.ifBlank { form.brand?.name ?: "Unknown" }
        val tempRanges = form.tempRanges
        return OpenSpoolPayload(
            type = material,
            colorHex = form.colorHex,
            brand = brandName,
            minTemp = tempRanges.extruderMin?.toString() ?: "190",
            maxTemp = tempRanges.extruderMax?.toString() ?: "220",
            bedMinTemp = tempRanges.bedMin?.toString(),
            bedMaxTemp = tempRanges.bedMax?.toString(),
            subtype = form.variant?.takeUnless { it.isBlank() } ?: "Basic",
            spoolId = null,
        )
    }

    private suspend fun awaitTerminalNfc(): NfcResult? =
        // Phase-aware wait: only the tap-wait is UX-bounded; the blocking
        // write I/O after the tap is covered by awaitTerminal's internal
        // grace budget so this coroutine never cancels a live write.
        nfc.awaitTerminal(WRITE_TAP_WAIT_MS)

    private companion object {
        /**
         * How long we wait for the user to physically tap a tag after Write
         * is armed. Preserves the old MainViewModel WRITE_TIMEOUT_MS_DEFAULT
         * (15s) UX budget. Once a tap is consumed, terminal I/O gets a
         * separate generous grace inside [NfcRepository.awaitTerminal] — the
         * old design wrapped everything in one 15s timeout and cancelled the
         * use case while the physical NDEF write was still in flight.
         */
        const val WRITE_TAP_WAIT_MS: Long = 15_000L
    }
}
