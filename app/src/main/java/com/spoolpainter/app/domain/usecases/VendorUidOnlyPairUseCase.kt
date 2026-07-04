package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.ui.screens.main.FormState
import javax.inject.Inject

data class VendorUidOnlyPairInput(
    val form: FormState,
    val newFilamentName: String,
    val newFilamentVendor: String,
    val resolvedMaterialName: String? = null,
    /** UID captured at Read time. Decoupled from `form.cardUid` so the use-case
     *  acts on the UID it was handed at Save press, even if the form drifts. */
    val observedUid: CardUid,
)

sealed interface VendorUidOnlyPairResult {
    sealed interface Success : VendorUidOnlyPairResult {
        data class UidPaired(
            val spoolId: Int,
            val uid: CardUid,
            val isNewSpool: Boolean,
        ) : Success
    }
    data class SpoolmanFailed(val uid: CardUid, val outcome: SpoolmanOutcome<*>) : VendorUidOnlyPairResult
    data class Cancelled(val reason: String) : VendorUidOnlyPairResult
    data class MoveOnBindPartial(
        val uid: CardUid,
        val partiallyModifiedSpoolId: Int,
        val reason: String,
    ) : VendorUidOnlyPairResult
}

/**
 * Vendor UID-only pair flow (FR-4.9). Runs the Spoolman pairing chain
 * (existing-spool PATCH or new-spool POST + PATCH) **without any NDEF write**.
 *
 * The constructor injects only [SpoolmanRepository] + [MoveOnBindUseCase] —
 * notably **no [com.spoolpainter.app.hardware.nfc.NfcRepository]**. Type
 * system enforces the "no NDEF write" invariant: a future careless edit
 * cannot reach `nfc.arm(...)` because the dependency is not in scope.
 *
 * Move-on-bind precheck runs **before** Spoolman mutation (symmetric with
 * [CreateAndPairUseCase]).
 */
open class VendorUidOnlyPairUseCase @Inject constructor(
    protected val spoolman: SpoolmanRepository,
    protected val moveOnBind: MoveOnBindUseCase,
) {

    open suspend operator fun invoke(input: VendorUidOnlyPairInput): VendorUidOnlyPairResult {
        val targetId = input.form.selectedSpoolId
        return if (targetId != null) {
            existingSpoolPath(input, targetId)
        } else {
            newSpoolPath(input)
        }
    }

    private suspend fun existingSpoolPath(
        input: VendorUidOnlyPairInput,
        selectedSpoolId: Int,
    ): VendorUidOnlyPairResult {
        when (val mob = moveOnBind.invoke(input.observedUid, selectedSpoolId)) {
            is MoveOnBindUseCase.Outcome.Proceed,
            is MoveOnBindUseCase.Outcome.Moved -> Unit
            is MoveOnBindUseCase.Outcome.Declined ->
                return VendorUidOnlyPairResult.Cancelled("repair declined")
            is MoveOnBindUseCase.Outcome.Failed -> {
                val partial = mob.partiallyModifiedSpoolIds.firstOrNull()
                return if (partial != null) {
                    VendorUidOnlyPairResult.MoveOnBindPartial(
                        input.observedUid, partial, mob.reason,
                    )
                } else {
                    VendorUidOnlyPairResult.SpoolmanFailed(
                        input.observedUid,
                        SpoolmanOutcome.ParseError(IllegalStateException(mob.reason)),
                    )
                }
            }
        }
        return when (val append = spoolman.appendCardUidToSpool(selectedSpoolId, input.observedUid)) {
            is SpoolmanOutcome.Success -> VendorUidOnlyPairResult.Success.UidPaired(
                spoolId = selectedSpoolId,
                uid = input.observedUid,
                isNewSpool = false,
            )
            else -> VendorUidOnlyPairResult.SpoolmanFailed(input.observedUid, append)
        }
    }

    private suspend fun newSpoolPath(
        input: VendorUidOnlyPairInput,
    ): VendorUidOnlyPairResult {
        // Move-on-bind precheck *before* the new spool exists. Sentinel
        // targetSpoolId = -1 tells the impl this is a "detach only" sweep —
        // the caller (this use-case) appends to the new spool below.
        when (val mob = moveOnBind.invoke(input.observedUid, NEW_SPOOL_SENTINEL)) {
            is MoveOnBindUseCase.Outcome.Proceed,
            is MoveOnBindUseCase.Outcome.Moved -> Unit
            is MoveOnBindUseCase.Outcome.Declined ->
                return VendorUidOnlyPairResult.Cancelled("repair declined")
            is MoveOnBindUseCase.Outcome.Failed -> {
                val partial = mob.partiallyModifiedSpoolIds.firstOrNull()
                return if (partial != null) {
                    VendorUidOnlyPairResult.MoveOnBindPartial(
                        input.observedUid, partial, mob.reason,
                    )
                } else {
                    VendorUidOnlyPairResult.SpoolmanFailed(
                        input.observedUid,
                        SpoolmanOutcome.ParseError(IllegalStateException(mob.reason)),
                    )
                }
            }
        }

        val req = NewFilamentRequest.fromForm(
            form = input.form,
            name = input.newFilamentName,
            vendorName = input.newFilamentVendor,
        ).let { base ->
            if (input.resolvedMaterialName?.isNotBlank() == true) {
                base.copy(materialName = input.resolvedMaterialName)
            } else {
                base
            }
        }

        val createOutcome = spoolman.createSpoolForNewFilamentBundle(req)
        val bundle = (createOutcome as? SpoolmanOutcome.Success)?.data
            ?: return VendorUidOnlyPairResult.SpoolmanFailed(input.observedUid, createOutcome)
        val newId = bundle.spool.id ?: return VendorUidOnlyPairResult.SpoolmanFailed(
            input.observedUid,
            SpoolmanOutcome.ParseError(IllegalStateException("no spool id from createSpool")),
        )

        return when (val append = spoolman.appendCardUidToSpool(newId, input.observedUid)) {
            is SpoolmanOutcome.Success -> VendorUidOnlyPairResult.Success.UidPaired(
                spoolId = newId,
                uid = input.observedUid,
                isNewSpool = true,
            )
            else -> VendorUidOnlyPairResult.SpoolmanFailed(input.observedUid, append)
        }
    }

    companion object {
        /** Passed to [MoveOnBindUseCase.invoke] when the target spool doesn't
         *  exist yet (new-spool path). Implementation skips the "already on
         *  target" idempotency branch when it sees the sentinel. */
        const val NEW_SPOOL_SENTINEL: Int = -1
    }
}
