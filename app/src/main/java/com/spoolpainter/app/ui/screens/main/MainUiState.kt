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
    val diameterMm: Float? = DEFAULT_DIAMETER_MM,
    val densityGPerCm3: Float? = DEFAULT_PLA_DENSITY,
)

fun FormState.toExpanderOverrides(): ExpanderOverrides = ExpanderOverrides(
    density = densityGPerCm3,
    diameter = diameterMm,
    weight = fullSpoolWeightG,
    spoolWeight = emptySpoolWeightG,
    price = priceMajor,
)

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
private const val DEFAULT_FILAMENT_WEIGHT_G: Float = 1000f
private const val DEFAULT_DIAMETER_MM: Float = 1.75f
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
