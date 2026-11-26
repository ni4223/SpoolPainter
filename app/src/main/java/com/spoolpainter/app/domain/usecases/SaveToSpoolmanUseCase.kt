package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.ExpanderOverrides
import com.spoolpainter.app.data.remote.spoolman.SpoolPatchBody
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.data.remote.spoolman.UrlNotConfiguredException
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.ui.screens.main.FormState
import com.spoolpainter.app.ui.screens.main.initialWeightToSend
import com.spoolpainter.app.ui.screens.main.toExpanderOverrides
import javax.inject.Inject
import kotlinx.coroutines.Deferred


data class SaveToSpoolmanInput(
    val form: FormState,
    val newFilamentName: String,
    val newFilamentVendor: String,
    /** Material name with "Other → custom" already resolved (else falls back to form.material?.name). */
    val resolvedMaterialName: String? = null,
)

/**
 * U13 — Spoolman-only half of the old "Save & Write" combo. Resolves (creates
 * or selects) a spool record and applies any filament-scope variant or
 * spool-scope (remaining_weight + spool_weight) patches. Does NOT touch NFC.
 *
 * Sequence:
 *   1. Resolve the spool: pick the existing one or create vendor + filament
 *      + spool (no `extra.card_uids` yet — UID linkage rides the Write tap).
 *   1a. Existing-spool variant edit (the one filament-scope field still
 *       editable on this path per decision K).
 *   1b. Existing-spool spool-scope edits (remaining_weight + spool_weight)
 *       behind the stale-prefill guard.
 *
 * Failures from 1a / 1b don't roll the spool back, but they are reported:
 * U32 B2 — the result is [SaveToSpoolmanResult.Failed] when a PATCH failed,
 * [SaveToSpoolmanResult.Success.Saved] only when an HTTP write actually
 * happened, and [SaveToSpoolmanResult.NoChanges] otherwise.
 *
 * `lastResolvedSpoolId` rides here so the caller can pin the dropdown selection
 * after a Save. Save creates spools; Write only maps a UID and never deletes
 * one, so there is no orphan-cleanup handoff.
 */
open class SaveToSpoolmanUseCase @Inject constructor(
    protected val spoolman: SpoolmanRepository,
) {

    /** Spool id from the most recent invoke(), regardless of new-vs-existing.
     *  Lets the caller pin selection after Save. */
    @Volatile
    var lastResolvedSpoolId: Int? = null
        private set

    open suspend operator fun invoke(snapshot: SaveToSpoolmanInput): SaveToSpoolmanResult {
        lastResolvedSpoolId = null

        // 1. Resolve the spool: existing selection or fresh create.
        val resolved = resolveSpool(snapshot)
        val (spoolId, isNewSpool) = when (resolved) {
            is ResolvedSpool.Existing -> resolved.id to false
            is ResolvedSpool.Created -> resolved.id to true
            is ResolvedSpool.Failed -> return resolved.result
        }
        val articleNumberFill = (resolved as? ResolvedSpool.Created)?.articleNumberFill
        lastResolvedSpoolId = spoolId

        var dirty = isNewSpool
        var failure: SpoolmanOutcome<*>? = null

        // 1a. Existing-spool filament-record edits (v2.1 — UI-13 followup).
        //     Color + Density + Diameter + Filament weight + Temps + Variant
        //     all flow as filament PATCH overrides. sparseDiff in
        //     applyOverridesIfNeeded collapses unchanged fields to a no-op,
        //     so passing the full bag on every Save is cheap. Material +
        //     Brand stay locked (changing those = wrong filament picked).
        //     Failures logged but do NOT abort the Save.
        val overrides = snapshot.form.toExpanderOverrides()
        if (!isNewSpool && overrides != ExpanderOverrides.EMPTY) {
            when (val patch = spoolman.applyOverridesToFilamentOfSpool(spoolId, overrides)) {
                is SpoolmanOutcome.Success -> if (patch.data.wrote) dirty = true
                else -> {
                    android.util.Log.w("SaveToSpoolman", "applyOverridesToFilamentOfSpool failed: $patch")
                    failure = patch
                }
            }
        }

        // 1b. Existing-spool spool-scope edits — remaining_weight + empty-spool
        //     + price (v2.1). Stale-prefill guard: PATCH only when the form
        //     value differs from the prefill snapshot. Price stays spool-scope
        //     only — Spoolman's COALESCE(spool.price, filament.price) means
        //     a per-spool override is the right surface; we DO NOT clobber
        //     the filament-record price (decision M, v2.0.2).
        if (!isNewSpool) {
            val rem = snapshot.form.remainingWeightG
            val remPrefilled = snapshot.form.prefilledRemainingWeightG
            val empty = snapshot.form.emptySpoolWeightG
            val emptyPrefilled = snapshot.form.prefilledEmptySpoolWeightG
            val price = snapshot.form.priceMajor
            val pricePrefilled = snapshot.form.prefilledPriceMajor
            val remDirty = rem != null && rem != remPrefilled
            val emptyDirty = empty != null && empty != emptyPrefilled
            val priceDirty = price != pricePrefilled
            if (remDirty || emptyDirty || priceDirty) {
                // U32 B3/FN2: set this spool's initial weight (never the
                // filament's weight) in the same PATCH when Spoolman would
                // cap or reject the remaining edit.
                val body = SpoolPatchBody(
                    initial_weight = snapshot.form.initialWeightToSend(),
                    remaining_weight = rem.takeIf { remDirty },
                    spool_weight = empty.takeIf { emptyDirty },
                    price = price.takeIf { priceDirty },
                )
                when (val patch = spoolman.patchSpoolFields(spoolId, body)) {
                    is SpoolmanOutcome.Success -> dirty = true
                    else -> {
                        android.util.Log.w("SaveToSpoolman", "patchSpoolFields failed: $patch")
                        failure = patch
                    }
                }
            }
        }

        return when {
            failure != null -> SaveToSpoolmanResult.Failed(failure)
            dirty -> SaveToSpoolmanResult.Success.Saved(
                spoolId = spoolId,
                isNewSpool = isNewSpool,
                articleNumberFill = articleNumberFill,
            )
            else -> SaveToSpoolmanResult.NoChanges(spoolId = spoolId)
        }
    }

    private sealed interface ResolvedSpool {
        data class Existing(val id: Int) : ResolvedSpool
        data class Created(
            val id: Int,
            val articleNumberFill: Deferred<SpoolmanOutcome<SpoolmanFilament>>? = null,
        ) : ResolvedSpool
        data class Failed(val result: SaveToSpoolmanResult) : ResolvedSpool
    }

    private suspend fun resolveSpool(snapshot: SaveToSpoolmanInput): ResolvedSpool {
        val targetId = snapshot.form.selectedSpoolId
        if (targetId != null) return ResolvedSpool.Existing(targetId)

        val filamentId = snapshot.form.selectedFilamentId
        return if (filamentId != null) {
            val createOutcome = spoolman.createSpoolForExistingFilament(
                filamentId,
                snapshot.form.toExpanderOverrides(),
            )
            translateCreateFailure(createOutcome)?.let { return it }
            val newSpool = (createOutcome as SpoolmanOutcome.Success).data
            val newId = newSpool.id ?: return ResolvedSpool.Failed(
                SaveToSpoolmanResult.Failed(
                    SpoolmanOutcome.ParseError(IllegalStateException("no spool id from createSpool")),
                ),
            )
            ResolvedSpool.Created(newId)
        } else {
            val bundleOutcome = spoolman.createSpoolForNewFilamentBundle(newFilamentRequest(snapshot))
            translateCreateFailure(bundleOutcome)?.let { return it }
            val bundle = (bundleOutcome as SpoolmanOutcome.Success).data
            val newId = bundle.spool.id ?: return ResolvedSpool.Failed(
                SaveToSpoolmanResult.Failed(
                    SpoolmanOutcome.ParseError(IllegalStateException("no spool id from createSpool")),
                ),
            )
            ResolvedSpool.Created(newId, bundle.articleNumberFill)
        }
    }

    private fun <T> translateCreateFailure(outcome: SpoolmanOutcome<T>): ResolvedSpool.Failed? {
        if (outcome is SpoolmanOutcome.Success) return null
        if (outcome is SpoolmanOutcome.NetworkError && outcome.cause is UrlNotConfiguredException) {
            return ResolvedSpool.Failed(SaveToSpoolmanResult.UrlNotConfigured)
        }
        return ResolvedSpool.Failed(SaveToSpoolmanResult.Failed(outcome))
    }

    private fun newFilamentRequest(snapshot: SaveToSpoolmanInput): NewFilamentRequest {
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
}
