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
    val form: FormState,
    val newFilamentName: String,
    val newFilamentVendor: String,
    /** Material name with "Other → custom" already resolved (else falls back to form.material?.name). */
    val resolvedMaterialName: String? = null,
)

/**
 * Create-and-pair orchestration. One sequence handles both tap-first (UID
 * already in the form) and form-first (no UID yet) — the only thing that
 * varies is what gets passed as `expectedUid` to the Write arm.
 *
 *   1. Resolve the spool: pick the existing one or create vendor + filament
 *      + spool (no `extra.card_uids` yet — the create call no longer lies
 *      about a UID it doesn't know).
 *   2. Arm Write with `expectedUid = form.cardUid` (null = accept any tap).
 *   3. The user's tap performs the write. The Success state's UID is the
 *      authoritative tag UID — same physical tap reused for capture.
 *   4. PATCH `extra.card_uids` to include the captured UID. Idempotent if it
 *      was already there (e.g. same spool re-paired).
 *   5. Verify pass on the next tap.
 *
 * Spoolman-first sequencing per FD §1 / FR-4.3 is preserved in spirit: the
 * spool record is committed *before* any tag is written, so a retry after a
 * verify-fail finds the spool via [SpoolmanRepository.findSpoolsByCardUid].
 */
open class CreateAndPairUseCase @Inject constructor(
    protected val nfc: NfcRepository,
    protected val spoolman: SpoolmanRepository,
    protected val moveOnBind: MoveOnBindUseCase,
) {

    open suspend operator fun invoke(snapshot: CreateAndPairInput): CreateAndPairResult {
        // 1. Resolve the spool: either an existing selection or a freshly
        //    minted vendor + filament + spool (no UID attached yet).
        val (spoolId, isNewSpool) = when (val resolved = resolveSpool(snapshot)) {
            is ResolvedSpool.Existing -> resolved.id to false
            is ResolvedSpool.Created -> resolved.id to true
            is ResolvedSpool.Failed -> return resolved.result
        }

        // 2. Arm Write — NfcRepository writes + verifies on the same physical
        //    tap. The use case accepts whichever tag the user taps; UID-
        //    enforcement (the prior `expectedUid` rejection) is removed —
        //    a stray tap is recoverable.
        val payload = makePayload(snapshot, spoolId = spoolId)
        val writeResult = armWriteAndAwait(payload)
        val tappedUid = when (writeResult) {
            is WriteResult.Success -> writeResult.uid
            is WriteResult.Verify -> return CreateAndPairResult.VerifyFailed(
                spoolId = spoolId,
                uid = writeResult.uid,
                isNewSpool = isNewSpool,
                cause = writeResult.reason,
            )
            is WriteResult.Failed -> return CreateAndPairResult.NfcFailed(
                snapshot.form.cardUid,
                writeResult.reason,
            )
        }

        // 3. Move-on-bind precheck (S-5.1 / S-5.2): runs BEFORE the append so
        //    a UID currently owned by another spool is moved (or the user
        //    declines) atomically. AmbiguousOwnership and Failed surface as
        //    SpoolmanFailed; Declined surfaces as Cancelled.
        when (val mob = moveOnBind.invoke(tappedUid, spoolId)) {
            is MoveOnBindUseCase.Outcome.Proceed,
            is MoveOnBindUseCase.Outcome.Moved -> Unit
            is MoveOnBindUseCase.Outcome.Declined ->
                return CreateAndPairResult.Cancelled(
                    "repair declined — UID still on the originally-paired spool",
                )
            is MoveOnBindUseCase.Outcome.Failed ->
                return CreateAndPairResult.SpoolmanFailed(
                    tappedUid,
                    SpoolmanOutcome.ParseError(IllegalStateException(mob.reason)),
                )
            is MoveOnBindUseCase.Outcome.AmbiguousOwnership ->
                return CreateAndPairResult.SpoolmanFailed(
                    tappedUid,
                    SpoolmanOutcome.ParseError(IllegalStateException(
                        "ambiguous ownership: spool ids " +
                            mob.currentOwners.mapNotNull { it.id }.joinToString(", "),
                    )),
                )
        }

        // 4. PATCH the spool to record the UID we just tapped. Idempotent.
        when (val append = spoolman.appendCardUidToSpool(spoolId, tappedUid)) {
            is SpoolmanOutcome.Success -> Unit
            else -> return CreateAndPairResult.SpoolmanFailed(tappedUid, append)
        }

        return CreateAndPairResult.Success.WrittenAndPaired(
            spoolId = spoolId,
            uid = tappedUid,
            isNewSpool = isNewSpool,
        )
    }

    private sealed interface ResolvedSpool {
        data class Existing(val id: Int) : ResolvedSpool
        data class Created(val id: Int) : ResolvedSpool
        data class Failed(val result: CreateAndPairResult) : ResolvedSpool
    }

    private suspend fun resolveSpool(snapshot: CreateAndPairInput): ResolvedSpool {
        val targetId = snapshot.form.selectedSpoolId
        if (targetId != null) return ResolvedSpool.Existing(targetId)

        val createOutcome = spoolman.createSpoolForNewFilament(newFilamentRequest(snapshot))
        val newSpool = (createOutcome as? SpoolmanOutcome.Success)?.data
            ?: return ResolvedSpool.Failed(
                CreateAndPairResult.SpoolmanFailed(snapshot.form.cardUid ?: CardUid(""), createOutcome),
            )
        val newId = newSpool.id ?: return ResolvedSpool.Failed(
            CreateAndPairResult.SpoolmanFailed(
                snapshot.form.cardUid ?: CardUid(""),
                SpoolmanOutcome.ParseError(IllegalStateException("no spool id from createSpool")),
            ),
        )
        return ResolvedSpool.Created(newId)
    }

    private sealed interface WriteResult {
        data class Success(val uid: CardUid) : WriteResult
        data class Verify(val uid: CardUid, val reason: String) : WriteResult
        data class Failed(val reason: String) : WriteResult
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
                    WriteResult.Failed("zero-length UID — non-NFC-A tag?")
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
                    WriteResult.Failed(outcome.reason)
                }
            else -> WriteResult.Failed("unexpected write state: $outcome")
        }
    }

    private fun newFilamentRequest(snapshot: CreateAndPairInput): NewFilamentRequest {
        val baseReq = NewFilamentRequest.fromForm(
            form = snapshot.form,
            name = snapshot.newFilamentName,
            vendorName = snapshot.newFilamentVendor,
        )
        val req = if (snapshot.resolvedMaterialName?.isNotBlank() == true) {
            baseReq.copy(materialName = snapshot.resolvedMaterialName)
        } else {
            baseReq
        }
        android.util.Log.d(
            "SpoolmanRepo",
            "newFilamentRequest: name=${req.name} variant=${req.variant} material=${req.materialName} colorHex=${req.colorHex}",
        )
        return req
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
