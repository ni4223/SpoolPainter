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
}

data class AmbiguityState(
    val uid: CardUid,
    val matches: List<SpoolmanSpool>,
    val classification: TagClassification,
)
