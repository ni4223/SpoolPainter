package com.spoolpainter.app.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoolpainter.app.data.local.SettingsRepository
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.data.remote.spoolman.UrlNotConfiguredException
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.domain.usecases.CreateAndPairInput
import com.spoolpainter.app.domain.usecases.CreateAndPairResult
import com.spoolpainter.app.domain.usecases.CreateAndPairUseCase
import com.spoolpainter.app.domain.usecases.MoveOnBindConfirmer
import com.spoolpainter.app.domain.usecases.ReadAndPairResult
import com.spoolpainter.app.domain.usecases.ReadAndPairUseCase
import com.spoolpainter.app.domain.usecases.TwoTagInput
import com.spoolpainter.app.domain.usecases.TwoTagResult
import com.spoolpainter.app.domain.usecases.TwoTagUseCase
import com.spoolpainter.app.hardware.nfc.NfcRepository
import com.spoolpainter.app.ui.common.UiEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val nfc: NfcRepository,
    spoolman: SpoolmanRepository,
    settings: SettingsRepository,
    private val readAndPair: ReadAndPairUseCase,
    private val createAndPair: CreateAndPairUseCase,
    private val twoTag: TwoTagUseCase,
    private val confirmer: MoveOnBindConfirmer,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    /** Free-text name typed when user picks Material → "Other". Cleared on form reset. */
    private val _customMaterial = MutableStateFlow("")
    val customMaterial: StateFlow<String> = _customMaterial.asStateFlow()

    /** Free-text name typed when user picks Brand → "Other". Cleared on form reset. */
    private val _customBrand = MutableStateFlow("")
    val customBrand: StateFlow<String> = _customBrand.asStateFlow()

    private var readJob: Job? = null
    private var writeJob: Job? = null
    private var priorActiveFlow: ActiveFlow? = null

    internal val readTimeoutMs: Long = READ_TIMEOUT_MS_DEFAULT
    internal val writeTimeoutMs: Long = WRITE_TIMEOUT_MS_DEFAULT

    val canWrite: StateFlow<Boolean> = combine(
        _state.map { it.form.canSubmit && it.activeFlow == ActiveFlow.Idle }.distinctUntilChanged(),
        _state.map { it.form.material }.distinctUntilChanged(),
        _customMaterial.map { it }.distinctUntilChanged(),
        _state.map { it.form.brand }.distinctUntilChanged(),
        _customBrand.map { it }.distinctUntilChanged(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val formOk = values[0] as Boolean
        val material = values[1] as Material?
        val customMat = values[2] as String
        val brand = values[3] as Brand?
        val customBr = values[4] as String
        formOk &&
            (material?.name != "Other" || customMat.isNotBlank()) &&
            (brand?.name != "Other" || customBr.isNotBlank())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            nfc.state.collect { value ->
                _state.update { it.copy(nfc = value) }
            }
        }
        viewModelScope.launch {
            nfc.lastSeenTag
                .map { it?.uid }
                .distinctUntilChanged()
                .collect { uid ->
                    if (uid != null) {
                        android.util.Log.d(
                            "SpoolmanRepo",
                            "lastSeenTag uid=${uid.hex} activeFlow=${_state.value.activeFlow} selectedSpoolId(before)=${_state.value.spoolman.selectedSpoolId}",
                        )
                        _state.update { it.copy(form = it.form.copy(cardUid = uid)) }
                    }
                }
        }
        viewModelScope.launch {
            spoolman.spools.collect { value ->
                android.util.Log.d(
                    "SpoolmanRepo",
                    "spools collected: count=${value.size} ids=${value.take(5).map { it.id }}..${value.takeLast(5).map { it.id }}",
                )
                _state.update { it.copy(spoolman = it.spoolman.copy(spools = value)) }
            }
        }
        viewModelScope.launch {
            settings.settings
                .map { it.url.isNotBlank() }
                .distinctUntilChanged()
                .collect { value ->
                    _state.update { it.copy(spoolman = it.spoolman.copy(urlConfigured = value)) }
                }
        }
        // Move-on-bind confirmation prompt: when a use-case asks the confirmer
        // for user input, surface the prompt as an ActiveFlow transition so the
        // bottom-sheet host renders. When the request resolves (null), restore
        // whatever flow was active beforehand — defensively, since the
        // continuation should write a fresh state shortly after.
        viewModelScope.launch {
            confirmer.pendingRequest.collect { req ->
                if (req != null) {
                    priorActiveFlow = _state.value.activeFlow
                    _state.update {
                        it.copy(
                            activeFlow = ActiveFlow.AwaitingRepairConfirmation(
                                uid = req.uid,
                                currentOwners = req.others,
                                targetSpoolId = req.targetSpoolId,
                            ),
                        )
                    }
                } else {
                    val prior = priorActiveFlow ?: ActiveFlow.Idle
                    _state.update { current ->
                        if (current.activeFlow is ActiveFlow.AwaitingRepairConfirmation) {
                            current.copy(activeFlow = prior)
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    fun onReadTapped() {
        if (_state.value.activeFlow != ActiveFlow.Idle) return
        readJob?.let { job ->
            if (job.isActive) {
                job.cancel()
                viewModelScope.launch { nfc.disarm() }
            }
        }
        _state.update { it.copy(activeFlow = ActiveFlow.ReadingForPair) }
        readJob = viewModelScope.launch {
            val result = withTimeoutOrNull(readTimeoutMs) { readAndPair.invoke() }
            if (result == null) {
                nfc.disarm()
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar("No tag tapped — try again"))
            } else {
                applyResult(result)
            }
        }
    }

    fun onWriteTapped() {
        if (!canWrite.value) return
        _state.update { it.copy(activeFlow = ActiveFlow.WritingForPair) }
        writeJob = viewModelScope.launch {
            val form = _state.value.form
            val materialName = resolveMaterialName(form.material, _customMaterial.value)
            val brandName = resolveBrandName(form.brand, _customBrand.value)
            val variantPart = form.variant?.trim().orEmpty()
            val derivedName = listOfNotNull(
                brandName.takeIf { it.isNotBlank() },
                materialName.takeIf { it.isNotBlank() },
                variantPart.takeIf { it.isNotBlank() },
            ).joinToString(" ").ifBlank { materialName }

            android.util.Log.d(
                "SpoolmanRepo",
                "onWriteTapped: form.variant=${form.variant} variantPart=$variantPart derivedName=$derivedName selectedSpoolId=${form.selectedSpoolId}",
            )

            val input = CreateAndPairInput(
                form = form,
                newFilamentName = derivedName,
                newFilamentVendor = brandName.ifBlank { "Generic" },
                resolvedMaterialName = materialName.takeIf { it.isNotBlank() },
            )
            val result = withTimeoutOrNull(writeTimeoutMs) {
                createAndPair.invoke(input)
            } ?: CreateAndPairResult.Cancelled("timeout")
            applyWriteResult(result)
        }
    }

    fun onSpoolSelected(spool: SpoolmanSpool?) {
        android.util.Log.d("SpoolmanRepo", "onSpoolSelected: spool.id=${spool?.id}")
        if (spool == null) {
            _state.update { current ->
                current.copy(
                    form = FormState(rawWriteMode = current.form.rawWriteMode),
                    spoolman = current.spoolman.copy(selectedSpoolId = null),
                    ambiguity = null,
                )
            }
            _customMaterial.value = ""
            _customBrand.value = ""
            return
        }
        if (spool.id == _state.value.form.selectedSpoolId) return
        _state.update { current ->
            current.copy(
                form = FormMapping.fromSpoolman(
                    spool = spool,
                    currentUid = current.form.cardUid,
                    rawWriteMode = current.form.rawWriteMode,
                    uidSource = FormMapping.SpoolmanUidSource.FromCardUidsOrClear,
                ),
                spoolman = current.spoolman.copy(selectedSpoolId = spool.id),
                ambiguity = null,
            )
        }
        _customMaterial.value = ""
        _customBrand.value = ""
    }

    fun onMaterialPicked(value: Material?) {
        _state.update { it.copy(form = it.form.copy(material = value)) }
        if (value?.name != "Other") {
            _customMaterial.value = ""
        }
        // When picking a known material, also seed the temperature defaults.
        if (value != null && value.name != "Other") {
            _state.update {
                it.copy(
                    form = it.form.copy(
                        tempRanges = TempRanges(
                            extruderMin = value.defaultMinTemp,
                            extruderMax = value.defaultMaxTemp,
                            bedMin = value.defaultBedMinTemp,
                            bedMax = value.defaultBedMaxTemp,
                        ),
                    ),
                )
            }
        }
    }

    fun onCustomMaterialChanged(value: String) {
        _customMaterial.value = value
    }

    fun onBrandPicked(value: Brand?) {
        _state.update { it.copy(form = it.form.copy(brand = value)) }
        if (value?.name != "Other") {
            _customBrand.value = ""
        }
    }

    fun onCustomBrandChanged(value: String) {
        _customBrand.value = value
    }

    fun onColorHexChanged(value: String?) {
        _state.update { it.copy(form = it.form.copy(colorHex = value)) }
    }

    fun onVariantChanged(value: String?) {
        _state.update { it.copy(form = it.form.copy(variant = value)) }
    }

    fun onTempRangesChanged(value: TempRanges) {
        _state.update { it.copy(form = it.form.copy(tempRanges = value)) }
    }

    fun onSettingsTapped() {
        _effects.trySend(UiEffect.Navigate("settings"))
    }

    private fun resolveMaterialName(material: Material?, custom: String): String {
        return if (material?.name == "Other" && custom.isNotBlank()) custom
        else material?.name ?: ""
    }

    private fun resolveBrandName(brand: Brand?, custom: String): String {
        return if (brand?.name == "Other" && custom.isNotBlank()) custom
        else brand?.name ?: ""
    }

    private fun applyResult(result: ReadAndPairResult) {
        when (result) {
            is ReadAndPairResult.Success.PrefillFromSpoolman -> {
                _state.update { current ->
                    val mapped = FormMapping.fromSpoolman(
                        result.spool,
                        result.uid,
                        current.form.rawWriteMode,
                    )
                    // If Spoolman didn't surface a variant but the tag's
                    // OpenSpool payload carries a subtype, prefer that — the
                    // tag is a legitimate fallback source for legacy filaments
                    // that pre-date U6a's extra.variant storage.
                    val tagVariant = (result.classification as? TagClassification.OpenSpool)
                        ?.payload?.subtype
                        ?.takeUnless { it == "Basic" || it.isBlank() }
                    val merged = if (mapped.variant == null && tagVariant != null) {
                        mapped.copy(variant = tagVariant)
                    } else {
                        mapped
                    }
                    current.copy(
                        form = merged,
                        spoolman = current.spoolman.copy(selectedSpoolId = result.spool.id),
                        ambiguity = null,
                        activeFlow = ActiveFlow.Idle,
                    )
                }
                _customMaterial.value = ""
                _customBrand.value = ""
            }
            is ReadAndPairResult.Success.PrefillFromTag -> {
                _state.update { current ->
                    current.copy(
                        form = FormMapping.fromOpenSpool(result.uid, result.payload, current.form.rawWriteMode),
                        spoolman = current.spoolman.copy(selectedSpoolId = null),
                        ambiguity = null,
                        activeFlow = ActiveFlow.Idle,
                    )
                }
                _customMaterial.value = ""
                _customBrand.value = ""
            }
            is ReadAndPairResult.Success.BlankForm -> {
                // Blank tag with no Spoolman match — keep whatever the user
                // was typing (material, color, temps, brand, variant) and
                // just update the UID + clear any prior spool selection.
                // This matches v1's UX: a Read on a blank tag is treated as
                // "I want to write this tag with my current form".
                _state.update { current ->
                    current.copy(
                        form = current.form.copy(
                            cardUid = result.uid,
                            selectedSpoolId = null,
                        ),
                        spoolman = current.spoolman.copy(selectedSpoolId = null),
                        ambiguity = null,
                        activeFlow = ActiveFlow.Idle,
                    )
                }
            }
            is ReadAndPairResult.Ambiguous -> {
                _state.update { current ->
                    current.copy(
                        form = FormMapping.blankForm(result.uid, current.form.rawWriteMode),
                        spoolman = current.spoolman.copy(selectedSpoolId = null),
                        ambiguity = AmbiguityState(result.uid, result.matches, result.classification),
                        activeFlow = ActiveFlow.Idle,
                    )
                }
            }
            is ReadAndPairResult.SpoolmanFailed -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar(humanReadable(result.outcome)))
            }
            is ReadAndPairResult.NfcFailed -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar(result.reason))
            }
            is ReadAndPairResult.Cancelled -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
            }
        }
    }

    private fun applyWriteResult(result: CreateAndPairResult) {
        when (result) {
            is CreateAndPairResult.Success.WrittenAndPaired -> {
                // First-tag write succeeded. Transition to PromptingPairAnother
                // so the bottom sheet asks "Pair another tag with this spool?".
                // The sheet's title acts as the success confirmation — a
                // separate snackbar would slide up underneath the sheet and be
                // immediately covered (UI-03).
                _state.update { current ->
                    current.copy(
                        form = current.form.copy(
                            cardUid = result.uid,
                            selectedSpoolId = result.spoolId,
                        ),
                        spoolman = current.spoolman.copy(selectedSpoolId = result.spoolId),
                        activeFlow = ActiveFlow.PromptingPairAnother(spoolId = result.spoolId),
                    )
                }
            }
            is CreateAndPairResult.VerifyFailed -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar("Verify failed. Tap Save to retry."))
            }
            is CreateAndPairResult.SpoolmanFailed -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar(humanReadable(result.outcome)))
            }
            is CreateAndPairResult.NfcFailed -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                val msg = if (result.reason.contains("vendor-tag", ignoreCase = true)) {
                    "Vendor tag — write blocked"
                } else {
                    "NFC error: ${result.reason}"
                }
                _effects.trySend(UiEffect.ShowSnackbar(msg))
            }
            is CreateAndPairResult.Cancelled -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                viewModelScope.launch { nfc.disarm() }
                // Move-on-bind decline already gave the user explicit choice
                // via the RepairConfirmSheet; emitting "No tag tapped" here is
                // misleading. Only show the snackbar for the genuine timeout
                // / no-tap path.
                if (!result.reason.startsWith("repair declined", ignoreCase = true)) {
                    _effects.trySend(UiEffect.ShowSnackbar("No tag tapped — try again"))
                }
            }
        }
    }

    fun onPairAnotherTagAccepted() {
        val current = _state.value.activeFlow as? ActiveFlow.PromptingPairAnother ?: return
        val spoolId = current.spoolId
        _state.update { it.copy(activeFlow = ActiveFlow.WritingSecondTag(spoolId)) }
        viewModelScope.launch {
            val result = withTimeoutOrNull(writeTimeoutMs) {
                twoTag.invoke(TwoTagInput(spoolId))
            } ?: TwoTagResult.Cancelled("timeout")
            applyTwoTagResult(result)
        }
    }

    fun onPairAnotherTagDismissed() {
        if (_state.value.activeFlow !is ActiveFlow.PromptingPairAnother) return
        // UI-06 + UI-10: preserve form AND spool selection so the user can see
        // what they just paired. They can manually clear via the dropdown's
        // "Clear selection" entry if they want a fresh entry.
        _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
        _effects.trySend(UiEffect.ShowSnackbar("Saved with one tag"))
    }

    fun onRepairResult(confirm: Boolean) {
        confirmer.submitResult(confirm)
    }

    private fun applyTwoTagResult(result: TwoTagResult) {
        when (result) {
            is TwoTagResult.Success.SecondTagPaired -> {
                // UI-06 + UI-10: preserve form AND spool selection so the
                // dropdown still shows the just-paired spool.
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar("Both tags paired"))
            }
            is TwoTagResult.VendorTagRejected -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar("Vendor tag — write blocked"))
            }
            is TwoTagResult.VerifyFailed -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar("Second-tag verify failed: ${result.cause}"))
            }
            is TwoTagResult.SpoolmanFailed -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar(humanReadable(result.outcome)))
            }
            is TwoTagResult.MoveOnBindPartial -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(
                    UiEffect.ShowSnackbar(
                        "Partial state in Spoolman — UID was removed from spool " +
                            "#${result.partiallyModifiedSpoolId}; restore manually if needed",
                    ),
                )
            }
            is TwoTagResult.NfcFailed -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar("Tag write failed: ${result.reason}"))
            }
            is TwoTagResult.Cancelled -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                // Suppress on explicit user decline (RepairConfirmSheet
                // Cancel). Emit only for genuine timeouts / unknown reasons.
                if (!result.reason.startsWith("repair declined", ignoreCase = true)) {
                    _effects.trySend(UiEffect.ShowSnackbar("Second-tag pairing cancelled (${result.reason})"))
                }
            }
        }
    }

    private fun humanReadable(outcome: SpoolmanOutcome<*>): String = when (outcome) {
        is SpoolmanOutcome.Success<*> -> "Spoolman call succeeded unexpectedly"
        is SpoolmanOutcome.HttpError -> "Spoolman returned ${outcome.code}: ${outcome.message}"
        is SpoolmanOutcome.NetworkError -> {
            if (outcome.cause is UrlNotConfiguredException) {
                "Spoolman URL not configured"
            } else {
                "Could not reach Spoolman: ${outcome.cause.message ?: outcome.cause::class.simpleName}"
            }
        }
        is SpoolmanOutcome.ParseError -> {
            // UI-08: when the cause is an IllegalStateException with a
            // descriptive message (e.g. "ambiguous ownership: spool ids 7, 8"),
            // surface the message directly rather than the misleading
            // "response could not be parsed" copy. AmbiguousOwnership and
            // similar logical conflicts are wrapped this way by the use-cases.
            val cause = outcome.cause
            if (cause is IllegalStateException) {
                val msg = cause.message.orEmpty()
                if (msg.startsWith("ambiguous ownership:")) {
                    val ids = msg.substringAfter("spool ids ", "")
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (ids.isNotEmpty()) {
                        "This tag is already paired with spools ${ids.joinToString(", ") { "#$it" }}. Fix in Spoolman first."
                    } else {
                        "This tag is paired with multiple spools. Fix in Spoolman first."
                    }
                } else if (msg.isNotBlank()) {
                    msg
                } else {
                    "Spoolman response could not be parsed"
                }
            } else {
                "Spoolman response could not be parsed"
            }
        }
    }

    private companion object {
        const val READ_TIMEOUT_MS_DEFAULT: Long = 10_000L
        const val WRITE_TIMEOUT_MS_DEFAULT: Long = 15_000L
    }
}
