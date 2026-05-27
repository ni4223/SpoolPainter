package com.spoolpainter.app.ui.screens.main

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
)

data class FormState(
    val cardUid: CardUid? = null,
    val material: Material? = null,
    val brand: Brand? = null,
    val colorHex: String? = null,
    val variant: String? = null,
    val tempRanges: TempRanges = TempRanges(),
    val selectedSpoolId: Int? = null,
    val rawWriteMode: Boolean = false,
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
        if (color.isNullOrBlank() || !color.matches(HEX6_REGEX)) return false
        if (ranges.extruderMin == null || ranges.extruderMax == null) return false
        if (ranges.extruderMin > ranges.extruderMax) return false
        if (ranges.bedMin != null && ranges.bedMax != null && ranges.bedMin > ranges.bedMax) return false
        return true
    }

private val HEX6_REGEX = Regex("^[0-9A-Fa-f]{6}$")

data class SpoolmanState(
    val spools: List<SpoolmanSpool> = emptyList(),
    val selectedSpoolId: Int? = null,
    val urlConfigured: Boolean = false,
)

sealed interface BannerState {
    data object Hidden : BannerState
    data class Offline(val lastError: String?) : BannerState
}

sealed interface ActiveFlow {
    data object Idle : ActiveFlow
    data object ReadingForPair : ActiveFlow
    data object WritingForPair : ActiveFlow
    data class PromptingPairAnother(val spoolId: Int) : ActiveFlow
    data class WritingSecondTag(val spoolId: Int) : ActiveFlow
    data class AwaitingRepairConfirmation(
        val uid: CardUid,
        val currentOwner: SpoolmanSpool,
        val targetSpoolId: Int,
    ) : ActiveFlow
}

data class AmbiguityState(
    val uid: CardUid,
    val matches: List<SpoolmanSpool>,
    val classification: TagClassification,
)
