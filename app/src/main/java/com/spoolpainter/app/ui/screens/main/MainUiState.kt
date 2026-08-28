package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.data.local.SpoolSortKey
import com.spoolpainter.app.data.remote.spoolman.ExpanderOverrides
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification

typealias NfcState = NfcResult

data class MainUiState(
    val form: FormState = FormState(),
    val spoolman: SpoolmanState = SpoolmanState(),
    val nfc: NfcState = NfcResult.Idle,
    val banner: BannerState = BannerState.Hidden,
    val activeFlow: ActiveFlow = ActiveFlow.Idle,
    val ambiguity: AmbiguityState? = null,
    val observedTagKind: ObservedTagKind = ObservedTagKind.None,
    /** UID captured alongside observedTagKind. Sticky across dropdown
     *  selection / form reset; only cleared when the chip is dismissed or a
     *  non-vendor tag is observed afterward. Vendor Save dispatch uses this
     *  instead of form.cardUid (which the dropdown selection can overwrite). */
    val observedTagUid: CardUid? = null,
    val writeMode: WriteMode = WriteMode.Spoolman,
    val spoolSortKey: SpoolSortKey = SpoolSortKey.Id,
    val spoolSortDirection: SortDirection = SortDirection.Desc,
    val filamentSortKey: FilamentSortKey = FilamentSortKey.Id,
    val filamentSortDirection: SortDirection = SortDirection.Desc,
    val priceSuffix: String = "$",
    // U20 (UI-49) — scan-time surfacing. Passive hints only: when an unpaired
    // tag is read, the scorer records the good-match ids here **in rank order
    // (best match first)**. The Spool / Filament pickers float these to the top
    // WHEN OPENED, in this order; nothing selects, no other flow changes. Empty
    // = no floated group (pickers render as today). Cleared on a paired read, a
    // manual selection, and a new read.
    val scanSuggestedSpoolIds: List<Int> = emptyList(),
    val scanSuggestedFilamentIds: List<Int> = emptyList(),
)

/**
 * The state "Clear" produces: a blank form plus the derived selection / hint state
 * that a blank form implies, keeping only the two view-only toggles
 * ([FormState.rawWriteMode], [FormState.moreDetailsExpanded]) so the user stays on
 * the section they were looking at.
 *
 * Pure and extracted **so the action and the button's enabled state cannot drift
 * apart**. `MainViewModel.onClearAll` applies it; `MainViewModel.canClear` asks
 * whether applying it would change anything. One definition, so a field added to
 * the clear is automatically a field that un-greys the button.
 *
 * Note it does NOT cover the two custom-name buffers (`_customMaterial` /
 * `_customBrand`), which live outside [MainUiState]; `canClear` folds those in
 * separately, and that split is exactly the kind of thing worth a test.
 */
internal fun MainUiState.cleared(): MainUiState = copy(
    form = FormState(
        rawWriteMode = form.rawWriteMode,
        moreDetailsExpanded = form.moreDetailsExpanded,
    ),
    spoolman = spoolman.copy(selectedSpoolId = null),
    ambiguity = null,
    observedTagKind = ObservedTagKind.None,
    observedTagUid = null,
    scanSuggestedSpoolIds = emptyList(),
    scanSuggestedFilamentIds = emptyList(),
)

data class FormState(
    val cardUid: CardUid? = null,
    val material: Material? = DEFAULT_MATERIAL,
    val brand: Brand? = null,
    val colorHex: String? = DEFAULT_COLOR_HEX,
    val variant: String? = null,
    val tempRanges: TempRanges = DEFAULT_TEMP_RANGES,
    val selectedSpoolId: Int? = null,
    val rawWriteMode: Boolean = false,

    // U8-Δ-1 — filament picker selection (mutex with selectedSpoolId).
    val selectedFilamentId: Int? = null,

    // U8-Δ-2 — Spool metadata expander state + five overrides. Prefilled with
    // the same defaults the call site would send to Spoolman so the user
    // sees exactly what will be persisted; clearing a field reverts to the
    // call-site default at write time.
    val moreDetailsExpanded: Boolean = false,
    val emptySpoolWeightG: Float? = null,
    val priceMajor: Float? = null,
    val fullSpoolWeightG: Float? = DEFAULT_FILAMENT_WEIGHT_G,
    val densityGPerCm3: Float? = DEFAULT_PLA_DENSITY,

    // v2.0.2 — spool-scope edit fields with stale-prefill snapshots.
    // Remaining is the single source of truth; Measured = remaining +
    // emptySpoolWeightG is computed at the call site. Price is dispatched
    // to spool.price (decision M); Spoolman stores it on both the spool
    // and filament records, so the prefill snapshot tracks the spool side.
    val remainingWeightG: Float? = null,
    val prefilledRemainingWeightG: Float? = null,
    val prefilledPriceMajor: Float? = null,
    val prefilledEmptySpoolWeightG: Float? = null,

    // U13 (Cluster A) — radio-style weight picker (Spoolman parity). Only the
    // active method's input renders; the inactive method's field is hidden
    // entirely (decision: hide-not-disable, save vertical real estate).
    // Default = Measured (most scales report total weight).
    //
    // measuredEntry is a transient buffer for the case "active=Measured AND
    // emptySpoolWeightG is null". The user typed a measured value but we
    // can't yet derive remaining (no empty-spool reference). We hold the
    // raw entry here without committing remainingWeightG; once empty-spool
    // is set, the ViewModel commits remaining = measuredEntry − emptySpool.
    val weightMethod: WeightMethod = WeightMethod.Measured,
    val measuredEntry: Float? = null,
)

/**
 * U13 (Cluster A) — which weight measurement the user is entering. Spoolman
 * parity. Gross was dropped (locked plan §1.4 + Q-U13-2).
 *  - [Remaining]: net filament left on the spool (g). Source of truth on the
 *    PATCH wire — Spoolman's `spool.remaining_weight`.
 *  - [Measured]: total scale reading including empty spool (g). Converts to
 *    remaining via `measured − emptySpoolWeightG` at submit time when both
 *    are known.
 */
enum class WeightMethod { Remaining, Measured }

/**
 * Decision I/J: when an existing spool OR existing filament is selected,
 * filament-scope spec fields lock — the override bag carries variant only,
 * which is the single legitimate filament-record edit on those paths
 * (defence-in-depth; the use case routes through applyVariantToFilamentOfSpool).
 *
 * On the new-filament path, the full override bag flows so a brand-new
 * filament POST can carry density/weight/spool_weight/price along with the
 * standard 1.75mm diameter (decision M+N). spoolPrice is set to the same
 * priceMajor value so the spool record gets per-spool pricing too.
 */
fun FormState.toExpanderOverrides(): ExpanderOverrides {
    // Existing-spool path (v2.1 unlock — UI-13 follow-up): filament-record
    // edits Color + Density + Diameter + Filament weight + Temps now flow
    // alongside Variant. sparseDiff in the repo collapses unchanged values
    // to a no-op so passing these on every Save is cheap. Material + Brand
    // stay locked (changing those means "wrong filament picked", not edit).
    // Spool-scope fields (remaining_weight, price, empty-spool) ride
    // patchSpoolFields separately as before.
    if (selectedSpoolId != null) {
        return ExpanderOverrides(
            density = densityGPerCm3,
            diameter = null,
            weight = fullSpoolWeightG,
            colorHex = colorHex?.takeIf { it.matches(HEX6_REGEX) }?.uppercase(),
            extruderTemp = tempRanges.extruderMin,
            bedTemp = tempRanges.bedMin,
            variant = variant?.takeIf { it.isNotBlank() },
        )
    }
    // Filament-picker path: filament-spec locked at the UI layer, but the
    // new spool gets per-spool empty-spool + price set from the form.
    if (selectedFilamentId != null) {
        return ExpanderOverrides(
            spoolWeightForSpool = emptySpoolWeightG,
            spoolPrice = priceMajor,
            variant = variant?.takeIf { it.isNotBlank() },
        )
    }
    // New-filament path: full overrides. Empty-spool + price flow to BOTH
    // the filament record (default for sibling spools) AND the spool record
    // (per-spool override). Same defensive double-write as price (decision M).
    return ExpanderOverrides(
        density = densityGPerCm3,
        diameter = null,
        weight = fullSpoolWeightG,
        spoolWeight = emptySpoolWeightG,
        spoolWeightForSpool = emptySpoolWeightG,
        price = priceMajor,
        spoolPrice = priceMajor,
        variant = variant?.takeIf { it.isNotBlank() },
    )
}

/**
 * True when the form has enough content to issue a write.
 *
 * Note: UID is **not** required up-front. v1's contract — preserved here — is
 * that the user can fill the form for a fresh spool, hit Save & Write, and the
 * UID gets captured by the same NFC tap that performs the write. UID is
 * required only at write time, inside [CreateAndPairUseCase].
 *
 * Bed temps are optional (mirrors v1) — they're written if present and skipped
 * otherwise.
 */
val FormState.canSubmit: Boolean
    get() {
        val color = colorHex
        val ranges = tempRanges
        if (material == null) return false
        if (brand == null) return false
        if (color.isNullOrBlank() || !color.matches(HEX6_REGEX)) return false
        if (ranges.extruderMin == null || ranges.extruderMax == null) return false
        if (ranges.extruderMin > ranges.extruderMax) return false
        if (ranges.bedMin != null && ranges.bedMax != null && ranges.bedMin > ranges.bedMax) return false
        return true
    }

private val HEX6_REGEX = Regex("^[0-9A-Fa-f]{6}$")

// Form defaults — fresh form starts with sensible picks the user can override.
private val DEFAULT_MATERIAL: Material = Material(
    name = "PLA",
    defaultMinTemp = 190,
    defaultMaxTemp = 220,
    defaultBedMinTemp = 40,
    defaultBedMaxTemp = 65,
    density = 1.24f,
)
private const val DEFAULT_COLOR_HEX: String = "FFFFFF"
private val DEFAULT_TEMP_RANGES: TempRanges = TempRanges(
    extruderMin = 190,
    extruderMax = 220,
    bedMin = 40,
    bedMax = 65,
)
// Spool-metadata defaults match MaterialPresetSource.Companion constants —
// Spoolman requires density/diameter/weight > 0 on filament create, and PLA
// density 1.24 g/cm³ is the universal consumer baseline. Empty-spool weight
// + price stay null because they vary too much to default sensibly.
// Diameter no longer surfaces in the form (decision N) — always 1.75mm at
// CreateFilamentRequest time.
private const val DEFAULT_FILAMENT_WEIGHT_G: Float = 1000f
private const val DEFAULT_PLA_DENSITY: Float = 1.24f

data class SpoolmanState(
    val spools: List<SpoolmanSpool> = emptyList(),
    val selectedSpoolId: Int? = null,
    val urlConfigured: Boolean = false,
    val reachable: Boolean = true,
)

sealed interface BannerState {
    data object Hidden : BannerState
    data class Offline(val lastError: String?) : BannerState
}

sealed interface ActiveFlow {
    data object Idle : ActiveFlow
    data object ReadingForPair : ActiveFlow
    data object WritingForPair : ActiveFlow
    data class PromptingPairAnother(
        val spoolId: Int,
        /** True when the just-completed pair was a vendor UID-only pair (no NDEF
         *  payload was written). The PairAnotherTagSheet uses this to surface
         *  vendor-appropriate copy instead of "we'll write the same data" copy. */
        val isVendorPair: Boolean = false,
    ) : ActiveFlow
    data class WritingSecondTag(val spoolId: Int) : ActiveFlow
    data class AwaitingRepairConfirmation(
        val uid: CardUid,
        val currentOwners: List<SpoolmanSpool>,
        val targetSpoolId: Int,
    ) : ActiveFlow
    data object WritingRaw : ActiveFlow
    data object PairingVendorUidOnly : ActiveFlow
}

data class AmbiguityState(
    val uid: CardUid,
    val matches: List<SpoolmanSpool>,
    val classification: TagClassification,
)
