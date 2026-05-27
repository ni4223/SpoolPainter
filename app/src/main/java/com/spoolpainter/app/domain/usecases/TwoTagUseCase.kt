package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.hardware.nfc.NfcRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class TwoTagInput(val spoolId: Int)

sealed interface TwoTagResult {
    sealed interface Success : TwoTagResult {
        data class SecondTagPaired(val spoolId: Int, val uid: CardUid) : Success
    }
    data class VendorTagRejected(val uid: CardUid) : TwoTagResult
    data class VerifyFailed(val uid: CardUid, val cause: String) : TwoTagResult
    data class SpoolmanFailed(val uid: CardUid?, val outcome: SpoolmanOutcome<*>) : TwoTagResult
    data class NfcFailed(val uid: CardUid?, val reason: String) : TwoTagResult
    data class Cancelled(val reason: String) : TwoTagResult
    data class MoveOnBindPartial(
        val uid: CardUid,
        val partiallyModifiedSpoolId: Int,
        val reason: String,
    ) : TwoTagResult
}

open class TwoTagUseCase @Inject constructor(
    protected val nfc: NfcRepository,
    protected val spoolman: SpoolmanRepository,
    protected val moveOnBind: MoveOnBindUseCase,
) {

    open suspend operator fun invoke(input: TwoTagInput): TwoTagResult {
        val payload = when (val derived = derivePayload(input.spoolId)) {
            is DerivedPayload.Ok -> derived.payload
            is DerivedPayload.Fail -> return derived.result
        }

        nfc.arm(NfcIntent.Write(payload, expectedUid = null))
        val outcome = awaitTerminalNfc()

        val (tappedUid, classification) = when (outcome) {
            is NfcResult.Success ->
                if (outcome.uid.hex.isEmpty()) {
                    return TwoTagResult.NfcFailed(null, "zero-length UID — non-NFC-A tag?")
                } else {
                    outcome.uid to outcome.classification
                }
            is NfcResult.Error -> {
                val msg = outcome.reason
                if (msg.contains("vendor-tag", ignoreCase = true)) {
                    val uid = nfc.lastSeenTag.value?.uid
                    return TwoTagResult.VendorTagRejected(uid ?: CardUid(""))
                }
                if (msg.contains("verify mismatch", ignoreCase = true) ||
                    msg.contains("verification failed", ignoreCase = true)
                ) {
                    val uid = nfc.lastSeenTag.value?.uid ?: CardUid("")
                    return TwoTagResult.VerifyFailed(uid, msg)
                }
                return TwoTagResult.NfcFailed(nfc.lastSeenTag.value?.uid, msg)
            }
            else -> return TwoTagResult.NfcFailed(null, "unexpected write state: $outcome")
        }

        if (classification is TagClassification.Vendor) {
            return TwoTagResult.VendorTagRejected(tappedUid)
        }

        when (val mob = moveOnBind.invoke(tappedUid, input.spoolId)) {
            is MoveOnBindUseCase.Outcome.Proceed,
            is MoveOnBindUseCase.Outcome.Moved -> Unit
            is MoveOnBindUseCase.Outcome.Declined ->
                return TwoTagResult.Cancelled("repair declined — UID still on the originally-paired spool")
            is MoveOnBindUseCase.Outcome.Failed -> {
                val partial = mob.partiallyModifiedSpoolId
                return if (partial != null) {
                    TwoTagResult.MoveOnBindPartial(tappedUid, partial, mob.reason)
                } else {
                    TwoTagResult.SpoolmanFailed(
                        tappedUid,
                        SpoolmanOutcome.ParseError(IllegalStateException(mob.reason)),
                    )
                }
            }
            is MoveOnBindUseCase.Outcome.AmbiguousOwnership ->
                return TwoTagResult.SpoolmanFailed(
                    tappedUid,
                    SpoolmanOutcome.ParseError(IllegalStateException(
                        "ambiguous ownership: spool ids " +
                            mob.currentOwners.mapNotNull { it.id }.joinToString(", "),
                    )),
                )
        }

        when (val append = spoolman.appendCardUidToSpool(input.spoolId, tappedUid)) {
            is SpoolmanOutcome.Success -> Unit
            else -> return TwoTagResult.SpoolmanFailed(tappedUid, append)
        }

        return TwoTagResult.Success.SecondTagPaired(spoolId = input.spoolId, uid = tappedUid)
    }

    private suspend fun awaitTerminalNfc(): NfcResult =
        nfc.state.first { it is NfcResult.Success || it is NfcResult.Error }

    private sealed interface DerivedPayload {
        data class Ok(val payload: OpenSpoolPayload) : DerivedPayload
        data class Fail(val result: TwoTagResult) : DerivedPayload
    }

    private suspend fun derivePayload(spoolId: Int): DerivedPayload {
        val spool = spoolman.spools.value.firstOrNull { it.id == spoolId }
            ?: when (val outcome = spoolman.getSpool(spoolId)) {
                is SpoolmanOutcome.Success -> outcome.data
                else -> return DerivedPayload.Fail(TwoTagResult.SpoolmanFailed(null, outcome))
            }

        val filamentId = spool.filament.id
        val filament: SpoolmanFilament =
            spoolman.filaments.value.firstOrNull { it.id == filamentId }
                ?: when (val outcome = spoolman.getFilament(filamentId)) {
                    is SpoolmanOutcome.Success -> outcome.data
                    else -> return DerivedPayload.Fail(TwoTagResult.SpoolmanFailed(null, outcome))
                }

        val vendor: SpoolmanVendor? = filament.vendor?.id
            ?.let { id -> spoolman.vendors.value.firstOrNull { it.id == id } }
            ?: filament.vendor

        val variantFromExtra = (spool.extra?.get("variant") ?: filament.extra?.get("variant"))
            ?.let(::stripJsonQuotes)
            ?.takeUnless { it.isBlank() }

        return DerivedPayload.Ok(
            OpenSpoolPayload(
                type = filament.material ?: "PLA",
                colorHex = filament.color_hex,
                brand = vendor?.name ?: "Unknown",
                minTemp = filament.settings_extruder_temp?.toString() ?: "190",
                maxTemp = filament.settings_extruder_temp?.plus(20)?.toString() ?: "220",
                bedMinTemp = filament.settings_bed_temp?.toString(),
                bedMaxTemp = filament.settings_bed_temp?.plus(10)?.toString(),
                subtype = variantFromExtra ?: "Basic",
                spoolId = spool.id?.toString(),
            ),
        )
    }

    /**
     * Spoolman returns extra `text` fields as JSON-encoded strings (`"matte"`).
     * Strip the wrapping quotes if present.
     */
    private fun stripJsonQuotes(raw: String): String {
        if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length - 1)
        }
        return raw
    }
}
