package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.hardware.nfc.NfcRepository
import com.spoolpainter.app.ui.screens.main.FormState
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class CreateAndPairInput(
    /** Required — the spool was already resolved by [SaveToSpoolmanUseCase]. */
    val spoolId: Int,
    /** True when [spoolId] was created by the immediately-preceding Save. Used to
     *  set [CreateAndPairResult.Success.WrittenAndPaired.isNewSpool] for the
     *  PromptingPairAnother sheet copy ("with this new spool"). */
    val isNewSpool: Boolean,
    val form: FormState,
    val newFilamentName: String,
    val newFilamentVendor: String,
    /** Material name with "Other → custom" already resolved (else falls back to form.material?.name). */
    val resolvedMaterialName: String? = null,
)

/**
 * U13 — Write-only orchestration. Spoolman state was committed beforehand by
 * [SaveToSpoolmanUseCase]; this use case only arms NFC, writes the OpenSpool
 * payload, and PATCHes the captured UID into `extra.card_uids`.
 *
 *   1. (was step 1) Spool already exists — `snapshot.spoolId`.
 *   2. Arm Write. The user's tap performs write + readback on one connection.
 *   3. PATCH `extra.card_uids` with the observed UID (idempotent). Move-on-
 *      bind sweeps any existing owners first.
 *   4. Translate the write outcome into the final [CreateAndPairResult].
 */
open class CreateAndPairUseCase @Inject constructor(
    protected val nfc: NfcRepository,
    protected val spoolman: SpoolmanRepository,
    protected val moveOnBind: MoveOnBindUseCase,
) {

    open suspend operator fun invoke(snapshot: CreateAndPairInput): CreateAndPairResult {
        val spoolId = snapshot.spoolId
        val isNewSpool = snapshot.isNewSpool

        // 2. Arm Write — NfcRepository writes + verifies on the same physical
        //    tap. The use case accepts whichever tag the user taps; UID-
        //    enforcement (the prior `expectedUid` rejection) is removed —
        //    a stray tap is recoverable.
        val payload = makePayload(snapshot, spoolId = spoolId)
        val writeResult = armWriteAndAwait(payload)

        // The UID we observed during the tap, regardless of write outcome.
        // For Verify/Failed paths this lets us still commit the spool↔UID
        // link to Spoolman so the user's pairing is preserved even when the
        // tag bytes are messy (interrupted write, verify mismatch, etc.).
        val observedUid: CardUid? = when (writeResult) {
            is WriteResult.Success -> writeResult.uid
            is WriteResult.Verify -> writeResult.uid
            is WriteResult.Failed -> writeResult.uid
        }

        // 3. Best-effort: commit UID to Spoolman BEFORE deciding the final
        //    outcome. Skip if no UID was seen (tap never landed) or if a
        //    move-on-bind decline aborts the flow.
        if (observedUid != null && observedUid.hex.isNotEmpty()) {
            // Move-on-bind precheck (S-5.1 / S-5.2): runs BEFORE the append
            // so a UID currently owned by another spool is moved (or the
            // user declines) atomically. Multi-source conflicts (UID on 2+
            // spools) are swept in one confirmation.
            when (val mob = moveOnBind.invoke(observedUid, spoolId)) {
                is MoveOnBindUseCase.Outcome.Proceed,
                is MoveOnBindUseCase.Outcome.Moved -> Unit
                is MoveOnBindUseCase.Outcome.Declined ->
                    return CreateAndPairResult.Cancelled(
                        reason = "repair declined, UID still on the originally-paired spool",
                        spoolId = spoolId,
                    )
                is MoveOnBindUseCase.Outcome.Failed ->
                    return CreateAndPairResult.SpoolmanFailed(
                        observedUid,
                        SpoolmanOutcome.ParseError(IllegalStateException(mob.reason)),
                    )
            }
            // PATCH the spool to record the UID we just tapped. Idempotent.
            // We do this even on Verify/Failed write outcomes so the tag is
            // still mapped to the spool by serial when the on-tag write dies
            // mid-tap (e.g. an NTAG213 too small for our payload). If the
            // append itself fails, fall through to the write outcome — the
            // user can retry. The spool always pre-exists (Save created it);
            // Write only maps a UID, never creates or deletes a spool.
            when (val append = spoolman.appendCardUidToSpool(spoolId, observedUid)) {
                is SpoolmanOutcome.Success -> Unit
                else -> if (writeResult is WriteResult.Success) {
                    return CreateAndPairResult.SpoolmanFailed(observedUid, append)
                }
            }
        }

        // 4. Translate the write outcome into the final result. Spoolman is
        //    already up to date (or we tried) at this point.
        return when (writeResult) {
            is WriteResult.Success -> CreateAndPairResult.Success.WrittenAndPaired(
                spoolId = spoolId,
                uid = writeResult.uid,
                isNewSpool = isNewSpool,
            )
            is WriteResult.Verify -> CreateAndPairResult.VerifyFailed(
                spoolId = spoolId,
                uid = writeResult.uid,
                isNewSpool = isNewSpool,
                cause = writeResult.reason,
            )
            is WriteResult.Failed -> {
                // Vendor pre-block at NfcRepository.runWriteThenVerify rejects
                // vendor-classified tags before any NDEF write. Step 3 above
                // already PATCHed the spool with the observed UID — so the
                // pairing is complete, just without an NDEF payload (which
                // is correct for vendor tags). Treat as success so the
                // PromptingPairAnother sheet fires symmetric to a normal
                // create-and-pair. Genuine write failures (phone moved,
                // marginal field, etc.) still surface as NfcFailed.
                if (writeResult.reason.contains("vendor-tag", ignoreCase = true) &&
                    writeResult.uid != null
                ) {
                    CreateAndPairResult.Success.WrittenAndPaired(
                        spoolId = spoolId,
                        uid = writeResult.uid,
                        isNewSpool = isNewSpool,
                        isVendorPair = true,
                    )
                } else {
                    CreateAndPairResult.NfcFailed(
                        uid = writeResult.uid ?: snapshot.form.cardUid,
                        reason = writeResult.reason,
                        spoolId = spoolId,
                    )
                }
            }
        }
    }

    private sealed interface WriteResult {
        data class Success(val uid: CardUid) : WriteResult
        data class Verify(val uid: CardUid, val reason: String) : WriteResult
        data class Failed(val uid: CardUid?, val reason: String) : WriteResult
    }

    /**
     * Arms a Write and waits for the terminal NFC state. NfcRepository's
     * write path performs an immediate read-back compare on the same tag
     * connection, so a Success here means both the bytes were written and
     * the tag verifies — one physical tap, no second tap needed.
     */
    private suspend fun armWriteAndAwait(payload: OpenSpoolPayload): WriteResult {
        nfc.arm(NfcIntent.Write(payload, expectedUid = null))
        val outcome = awaitTerminalNfc()
        return when (outcome) {
            is NfcResult.Success ->
                if (outcome.uid.hex.isEmpty()) {
                    WriteResult.Failed(null, "zero-length UID, non-NFC-A tag?")
                } else {
                    WriteResult.Success(outcome.uid)
                }
            is NfcResult.Error ->
                if (outcome.reason.contains("verify mismatch", ignoreCase = true) ||
                    outcome.reason.contains("verification failed", ignoreCase = true)
                ) {
                    // Verify failure carries the UID via the lastSeenTag buffer.
                    val uid = nfc.lastSeenTag.value?.uid ?: CardUid("")
                    WriteResult.Verify(uid, outcome.reason)
                } else {
                    // Tag may have been seen (UID captured in lastSeenTag)
                    // even when the NDEF write threw — surface it so the
                    // caller can still commit the spool↔UID link to Spoolman.
                    val uid = nfc.lastSeenTag.value?.uid?.takeIf { it.hex.isNotEmpty() }
                    WriteResult.Failed(uid, outcome.reason)
                }
            else -> WriteResult.Failed(null, "unexpected write state: $outcome")
        }
    }

    private fun makePayload(snapshot: CreateAndPairInput, spoolId: Int): OpenSpoolPayload {
        val form = snapshot.form
        val material = snapshot.resolvedMaterialName?.takeIf { it.isNotBlank() }
            ?: form.material?.name
            ?: "PLA"
        val brandName = snapshot.newFilamentVendor.ifBlank { form.brand?.name ?: "Unknown" }
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
            spoolId = spoolId.toString(),
        )
    }

    private suspend fun awaitTerminalNfc(): NfcResult =
        nfc.state.first { it is NfcResult.Success || it is NfcResult.Error }
}
