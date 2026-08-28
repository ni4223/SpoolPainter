package com.spoolpainter.app.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoolpainter.app.data.local.MaterialBrandRepository
import com.spoolpainter.app.data.local.SettingsRepository
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.data.remote.spoolman.UrlNotConfiguredException
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.domain.primitives.ExtraCardUidsCodec
import com.spoolpainter.app.domain.primitives.SpoolMatchScorer
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.domain.usecases.CreateAndPairInput
import com.spoolpainter.app.domain.usecases.CreateAndPairResult
import com.spoolpainter.app.domain.usecases.CreateAndPairUseCase
import com.spoolpainter.app.domain.usecases.MoveOnBindConfirmer
import com.spoolpainter.app.domain.usecases.RawWriteInput
import com.spoolpainter.app.domain.usecases.RawWriteResult
import com.spoolpainter.app.domain.usecases.RawWriteUseCase
import com.spoolpainter.app.domain.usecases.ReadAndPairResult
import com.spoolpainter.app.domain.usecases.ReadAndPairUseCase
import com.spoolpainter.app.domain.usecases.SaveToSpoolmanInput
import com.spoolpainter.app.domain.usecases.SaveToSpoolmanResult
import com.spoolpainter.app.domain.usecases.SaveToSpoolmanUseCase
import com.spoolpainter.app.domain.usecases.TwoTagInput
import com.spoolpainter.app.domain.usecases.TwoTagResult
import com.spoolpainter.app.domain.usecases.TwoTagUseCase
import com.spoolpainter.app.domain.usecases.VendorUidOnlyPairInput
import com.spoolpainter.app.domain.usecases.VendorUidOnlyPairResult
import com.spoolpainter.app.domain.usecases.VendorUidOnlyPairUseCase
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
import kotlinx.datetime.Clock
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val nfc: NfcRepository,
    private val spoolman: SpoolmanRepository,
    private val settings: SettingsRepository,
    private val materialBrandRepo: MaterialBrandRepository,
    private val readAndPair: ReadAndPairUseCase,
    private val saveToSpoolman: SaveToSpoolmanUseCase,
    private val createAndPair: CreateAndPairUseCase,
    private val twoTag: TwoTagUseCase,
    private val confirmer: MoveOnBindConfirmer,
    private val rawWrite: RawWriteUseCase,
    private val vendorUidOnlyPair: VendorUidOnlyPairUseCase,
) : ViewModel() {

    /** All filaments from Spoolman. Filament picker reads from this. */
    val filaments: StateFlow<List<SpoolmanFilament>> = spoolman.filaments

    /** Merged preset + user-added materials (case-insensitive dedup, presets first). */
    val materials: StateFlow<List<Material>> = materialBrandRepo.materials

    /** Spoolman vendors verbatim, plus presets the user has no vendor for (UI-63). */
    val brands: StateFlow<List<String>> = materialBrandRepo.brands

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
    private var saveJob: Job? = null

    /**
     * UI-54 — the form the last spool selection derived, kept so a Spoolman
     * refresh can tell an untouched form (safe to re-derive from fresh data)
     * from one the user has edited (must not be clobbered). Null whenever no
     * spool is selected. Compared by value, so it is deliberately the whole
     * [FormState] rather than a dirty bit: any field the user changes makes the
     * comparison fail and suppresses the re-derive.
     */
    private var selectionFormSnapshot: FormState? = null
    private val _saveInFlight = MutableStateFlow(false)
    val saveInFlight: StateFlow<Boolean> = _saveInFlight.asStateFlow()
    private var priorActiveFlow: ActiveFlow? = null
    // UI-02: passive-tap hint. The hint is suppressed when the user has acted
    // (Read pressed, spool picked, write started). When the user keeps tapping
    // without acting, re-fire the hint after a 15s cooldown so the second/third
    // tap also gets help. Stored as the wall-clock instant of the last hint.
    private var lastAmbientHintEpochMs: Long = 0L

    internal val readTimeoutMs: Long = READ_TIMEOUT_MS_DEFAULT
    internal val writeTimeoutMs: Long = WRITE_TIMEOUT_MS_DEFAULT

    /**
     * U13 — true when the form can be saved to Spoolman (HTTP-only). Requires
     * a fully-validated form with resolved Other-custom names. Save creates a
     * spool on the new-filament path or PATCHes on the existing-spool path.
     * RawNoUrl mode disables Save (no Spoolman target).
     */
    val canSave: StateFlow<Boolean> = combine(
        _state.map {
            it.form.canSubmit &&
                it.activeFlow == ActiveFlow.Idle &&
                it.writeMode == WriteMode.Spoolman
        }.distinctUntilChanged(),
        _state.map { it.form.material }.distinctUntilChanged(),
        _customMaterial.map { it }.distinctUntilChanged(),
        _state.map { it.form.brand }.distinctUntilChanged(),
        _customBrand.map { it }.distinctUntilChanged(),
        _saveInFlight,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val baseOk = values[0] as Boolean
        val material = values[1] as Material?
        val customMat = values[2] as String
        val brand = values[3] as Brand?
        val customBr = values[4] as String
        val saving = values[5] as Boolean
        val customsOk = (material?.name != "Other" || customMat.isNotBlank()) &&
            (brand?.name != "Other" || customBr.isNotBlank())
        baseOk && customsOk && !saving
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * U13 — true when Write is tappable.
     *
     * Vendor + Spoolman + chip visible: Save handles the vendor pair (Q-U13-1=A);
     * Write button stays disabled in that state. Write button caption tells the
     * user what to do ("Pick a spool or hit Save first." / "Vendor tag — can't
     * be written.").
     *
     * RawNoUrl: Write writes the form to a tag (no Spoolman); enabled when the
     * form validates.
     *
     * Standard (Spoolman + non-vendor): enabled when a spool is selected.
     */
    val canWrite: StateFlow<Boolean> = combine(
        _state.map {
            it.activeFlow == ActiveFlow.Idle
        }.distinctUntilChanged(),
        _state.map { it.form.canSubmit }.distinctUntilChanged(),
        _state.map { it.form.material }.distinctUntilChanged(),
        _customMaterial.map { it }.distinctUntilChanged(),
        _state.map { it.form.brand }.distinctUntilChanged(),
        _customBrand.map { it }.distinctUntilChanged(),
        _state.map { it.spoolman.selectedSpoolId }.distinctUntilChanged(),
        _state.map { it.observedTagKind }.distinctUntilChanged(),
        _state.map { it.writeMode }.distinctUntilChanged(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val idle = values[0] as Boolean
        val formValid = values[1] as Boolean
        val material = values[2] as Material?
        val customMat = values[3] as String
        val brand = values[4] as Brand?
        val customBr = values[5] as String
        val selectedSpoolId = values[6] as Int?
        val tagKind = values[7] as ObservedTagKind
        val mode = values[8] as WriteMode
        if (!idle) return@combine false
        val customsOk = (material?.name != "Other" || customMat.isNotBlank()) &&
            (brand?.name != "Other" || customBr.isNotBlank())
        when (mode) {
            // Vendor tags can't be NDEF-written. RawNoUrl + Vendor is a
            // dead end (no Spoolman target either). Disable Write so the
            // vendor caption can fire and the user knows to configure
            // Spoolman first.
            WriteMode.RawNoUrl -> tagKind != ObservedTagKind.Vendor && formValid && customsOk
            WriteMode.Spoolman -> {
                // 2026-06-06 reframe: vendor tags now route through Write
                // (HTTP-only UID append) so each button has one job. Save
                // = form edits; Write = NFC + UID for writable, HTTP-only
                // UID for vendor. Both vendor and writable paths require
                // a spool target; canWrite enables once one exists.
                selectedSpoolId != null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Read button is active (showing "Cancel"). Drives the bottom-bar label flip. */
    val isReadInFlight: StateFlow<Boolean> =
        _state.map { it.activeFlow == ActiveFlow.ReadingForPair }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Write button is currently in a tag-waiting NDEF flow that supports Cancel.
     *  Includes second-tag listening from the pair-another flow so the inline
     *  [Read|Write] row's shared Cancel button covers it too — sheet auto-
     *  dismisses during second-tag listening so the user only sees one
     *  Cancel surface. Vendor UID-only pair is HTTP-only and is NOT
     *  cancellable (button stays disabled while in flight; ~250ms typical
     *  roundtrip). */
    val isWriteCancellable: StateFlow<Boolean> =
        _state.map {
            it.activeFlow == ActiveFlow.WritingForPair ||
                it.activeFlow == ActiveFlow.WritingRaw ||
                it.activeFlow is ActiveFlow.WritingSecondTag
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * U24 (UI-59) — the filament ids the Filament picker floats when opened, in
     * rank order (best first). Two sources, one precedence:
     *
     *  1. A scan set, if one exists — U20's verified behaviour, untouched.
     *  2. Otherwise the **form's own** fields, via [SpoolMatchScorer.formQuery],
     *     but only while nothing is selected. With a spool or filament selected
     *     the identity fields are locked and their values are the selection's
     *     own, so a float would just re-list the selection (and today's
     *     behaviour in that state is no float).
     *
     * Derived, and deliberately **not** written back into [_state]: a
     * state → compute → state round trip would be a recomposition loop waiting
     * to happen, and this shape makes it structurally impossible.
     * [MainUiState.scanSuggestedFilamentIds] keeps meaning "the scan set" —
     * U20's clear-on-selection / clear-on-new-read logic depends on that.
     */
    val suggestedFilamentIds: StateFlow<List<Int>> = combine(
        _state.map { s ->
            FormSuggestionSignals(
                scanSuggestedFilamentIds = s.scanSuggestedFilamentIds,
                hasSelection = s.form.selectedFilamentId != null || s.form.selectedSpoolId != null,
                material = s.form.material?.name,
                brand = s.form.brand?.name,
                colorHex = s.form.colorHex,
                variant = s.form.variant,
            )
        }.distinctUntilChanged(),
        spoolman.filaments,
    ) { signals, inventory ->
        if (signals.scanSuggestedFilamentIds.isNotEmpty()) return@combine signals.scanSuggestedFilamentIds
        if (signals.hasSelection) return@combine emptyList()
        val query = SpoolMatchScorer.formQuery(
            material = signals.material,
            brand = signals.brand,
            colorHex = signals.colorHex,
            variant = signals.variant,
        ) ?: return@combine emptyList()
        SpoolMatchScorer.suggestedFilamentIds(query, matchCandidates(inventory))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // F-6 (v2.0.3): drives the MainScreen PullToRefreshBox spinner. Flips
    // true while a user-initiated refresh is in flight, false when it
    // returns (success or failure). Internal-throttled refreshes (foreground,
    // Read-arm) don't surface here — those are silent.
    private val _isSpoolmanRefreshing = MutableStateFlow(false)
    val isSpoolmanRefreshing: StateFlow<Boolean> = _isSpoolmanRefreshing.asStateFlow()

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
                reDeriveSelectedSpoolForm(value)
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
        viewModelScope.launch {
            settings.settings
                .map { s ->
                    SortProjection(
                        spoolKey = s.spoolSortKey,
                        spoolDir = s.spoolSortDirection,
                        filamentKey = s.filamentSortKey,
                        filamentDir = s.filamentSortDirection,
                        priceSuffix = s.currency.symbol,
                    )
                }
                .distinctUntilChanged()
                .collect { p ->
                    _state.update {
                        it.copy(
                            spoolSortKey = p.spoolKey,
                            spoolSortDirection = p.spoolDir,
                            filamentSortKey = p.filamentKey,
                            filamentSortDirection = p.filamentDir,
                            priceSuffix = p.priceSuffix,
                        )
                    }
                }
        }
        // WriteMode is derived from settings.url. No connectivity check — a
        // transient unreachable Spoolman should not silently flip the app
        // into raw-write mode.
        viewModelScope.launch {
            settings.settings
                .map { it.url.isNotBlank() }
                .distinctUntilChanged()
                .collect { configured ->
                    val mode = if (configured) WriteMode.Spoolman else WriteMode.RawNoUrl
                    _state.update { it.copy(writeMode = mode) }
                }
        }
        // Offline banner + reachable flag: only when URL is configured AND
        // Spoolman is currently unreachable. Hidden when URL is blank (no
        // Spoolman in play, no banner needed) or when Spoolman is reachable.
        viewModelScope.launch {
            combine(
                settings.settings.map { it.url.isNotBlank() }.distinctUntilChanged(),
                spoolman.connectivity,
            ) { urlConfigured, conn ->
                val isUnreachable = conn is com.spoolpainter.app.data.remote.spoolman.ConnectivityState.Unreachable
                val banner = if (urlConfigured && isUnreachable) {
                    BannerState.Offline(
                        (conn as com.spoolpainter.app.data.remote.spoolman.ConnectivityState.Unreachable).reason,
                    )
                } else {
                    BannerState.Hidden
                }
                banner to !isUnreachable
            }.distinctUntilChanged().collect { (banner, reachable) ->
                _state.update {
                    it.copy(
                        banner = banner,
                        spoolman = it.spoolman.copy(reachable = reachable),
                    )
                }
            }
        }
        // ObservedTagKind only flips on EXPLICIT Read (nfc.state.Success).
        // Passive ambient taps surface a snackbar hint but DO NOT change
        // app state — vendor mode would otherwise stick from a random
        // pass-by tap, breaking the "I want to map this tag now" flow
        // (2026-06-06 fix). The lastSeenTag collector below stays for
        // the snackbar; it just no longer mutates `observedTagKind`.
        viewModelScope.launch {
            nfc.lastSeenTag.collect { tag ->
                // UI-02: passive-tap hint with cooldown. Fires when the tap is
                // genuinely ambient (no read/write in flight). Re-fires on
                // subsequent taps if 15s have elapsed since the last hint,
                // so a user who keeps tapping without pressing Read gets help
                // again. Copy varies by classification.
                if (
                    tag != null &&
                    _state.value.activeFlow == ActiveFlow.Idle
                ) {
                    val nowMs = Clock.System.now().toEpochMilliseconds()
                    if (nowMs - lastAmbientHintEpochMs >= AMBIENT_HINT_COOLDOWN_MS) {
                        lastAmbientHintEpochMs = nowMs
                        val message = when (tag.classification) {
                            is TagClassification.Vendor ->
                                "Vendor tag. Press Read to load."
                            is TagClassification.Blank ->
                                "Blank tag detected."
                            is TagClassification.OpenSpool ->
                                "Tag detected. Press Read to load."
                            null ->
                                "Tag detected. Press Read to load."
                        }
                        _effects.trySend(UiEffect.ShowSnackbar(message))
                    }
                }
            }
        }
        viewModelScope.launch {
            nfc.state.collect { value ->
                if (value is com.spoolpainter.app.domain.primitives.NfcResult.Success) {
                    val kind = mapClassification(value.classification)
                    if (kind != null) {
                        _state.update {
                            it.copy(observedTagKind = kind, observedTagUid = value.uid)
                        }
                    }
                }
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

    /**
     * F-6 (v2.0.3) — explicit user-initiated pull-to-refresh on MainScreen.
     * Forces a refresh (bypasses the 5s throttle) and surfaces the in-flight
     * state through [isSpoolmanRefreshing] so the Material 3 PullToRefreshBox
     * spinner stays visible until the refresh returns. Quiet on failure —
     * the user already gets banner-level feedback through the
     * `state.spoolman.reachable` / `state.banner` channels.
     */
    fun onPullToRefresh() {
        if (_isSpoolmanRefreshing.value) return
        viewModelScope.launch {
            _isSpoolmanRefreshing.value = true
            try {
                runCatching { spoolman.refreshIfStale(force = true) }
            } finally {
                _isSpoolmanRefreshing.value = false
            }
        }
    }

    /**
     * U13 — Read tap is a toggle. Idle → start a fresh read. ReadingForPair →
     * cancel the in-flight read + disarm NFC + return to Idle (no snackbar;
     * Cancel is an explicit user action).
     */
    fun onReadTapped() {
        val current = _state.value.activeFlow
        if (current == ActiveFlow.ReadingForPair) {
            cancelReadInFlight()
            return
        }
        if (current != ActiveFlow.Idle) return
        readJob?.let { job ->
            if (job.isActive) {
                job.cancel()
                viewModelScope.launch { nfc.disarm() }
            }
        }
        // F-6 (v2.0.3): kick a Spoolman refresh in parallel with the NFC arm
        // so a spool created in the web UI between the last refresh and this
        // tap is in the cache by the time the tag prefill resolves. Throttled
        // (5s) and Mutex-serialised inside the repo, so a Read+resume race
        // collapses to a single refresh. Fire-and-forget — Read doesn't gate
        // on refresh success.
        viewModelScope.launch {
            runCatching { spoolman.refreshIfStale() }
        }
        // Clear any prior scan surfacing hints at read-start so none survives
        // into a new read regardless of which outcome branch runs. The prefill
        // branches re-populate them fresh when they apply.
        _state.update {
            it.copy(
                activeFlow = ActiveFlow.ReadingForPair,
                scanSuggestedSpoolIds = emptyList(),
                scanSuggestedFilamentIds = emptyList(),
            )
        }
        readJob = viewModelScope.launch {
            val result = withTimeoutOrNull(readTimeoutMs) { readAndPair.invoke() }
            if (result == null) {
                nfc.disarm()
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar("No tag tapped. Try again."))
            } else {
                applyResult(result)
            }
        }
    }

    private fun cancelReadInFlight() {
        readJob?.cancel()
        readJob = null
        viewModelScope.launch { nfc.disarm() }
        _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
    }

    private fun cancelWriteInFlight() {
        writeJob?.cancel()
        writeJob = null
        viewModelScope.launch { nfc.disarm() }
        _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
    }

    /**
     * U13 — Save to Spoolman. HTTP-only; no NFC. On vendor + Spoolman + chip
     * visible (Q-U13-1=A), Save instead routes to the vendor UID-only pair
     * use case — Save = "commit Spoolman state + UID linkage" subsumes the
     * vendor pair on this path. Write button stays disabled with chip showing.
     */
    fun onSaveTapped() {
        if (!canSave.value) return
        if (_saveInFlight.value) return
        val state = _state.value
        val form = state.form
        // 2026-06-06 reframe: vendor UID mapping moved off Save and onto
        // Write so each button has one job. Save = pure HTTP form edits
        // across all states (vendor or otherwise). Write = NFC + UID for
        // writable tags, or HTTP-only UID append for vendor tags. See
        // [onWriteTapped] vendor branch.
        // Pure HTTP Save: no activeFlow transition. Save runs in viewModelScope
        // and the Save button's disabled state during the coroutine is the only
        // visual feedback. ~250 ms typical Spoolman roundtrip is too short to
        // justify a screen-blocking flow + Cancel surface.
        _saveInFlight.value = true
        saveJob = viewModelScope.launch {
            try {
                val materialName = resolveMaterialName(form.material, _customMaterial.value)
                val brandName = resolveBrandName(form.brand, _customBrand.value)
                val variantPart = form.variant?.trim().orEmpty()
                val derivedName = listOfNotNull(
                    brandName.takeIf { it.isNotBlank() },
                    materialName.takeIf { it.isNotBlank() },
                    variantPart.takeIf { it.isNotBlank() },
                ).joinToString(" ").ifBlank { materialName }
                val input = SaveToSpoolmanInput(
                    form = form,
                    newFilamentName = derivedName,
                    newFilamentVendor = brandName.ifBlank { "Generic" },
                    resolvedMaterialName = materialName.takeIf { it.isNotBlank() },
                )
                val result = withTimeoutOrNull(writeTimeoutMs) {
                    saveToSpoolman.invoke(input)
                } ?: SaveToSpoolmanResult.Failed(
                    com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome.NetworkError(
                        java.io.IOException("Save timed out"),
                    ),
                )
                applySaveResult(result)
            } finally {
                _saveInFlight.value = false
            }
        }
    }

    /**
     * U13 — Write to NFC. Tag-only; assumes Spoolman state was already
     * committed by an immediately-preceding Save (or a prior Read/spool-pick).
     *
     * Toggle behaviour: when a tag-waiting Write is in flight, this cancels it.
     */
    fun onWriteTapped() {
        val state = _state.value
        if (state.activeFlow == ActiveFlow.WritingForPair ||
            state.activeFlow == ActiveFlow.WritingRaw
        ) {
            cancelWriteInFlight()
            return
        }
        if (state.activeFlow is ActiveFlow.WritingSecondTag) {
            // Shared inline Cancel covers second-tag listening too. Delegate
            // to the existing pair-another toggle, which cancels the writeJob
            // + disarms NFC + flips activeFlow back to PromptingPairAnother
            // so the sheet re-mounts at its prompt state.
            onPairAnotherTagAccepted()
            return
        }
        if (!canWrite.value) return
        val form = state.form
        val mode = state.writeMode
        // 2026-06-06 reframe: vendor tags can't be NDEF-written, but the
        // UID still needs to land in Spoolman's `extra.card_uids`. Route
        // vendor + Spoolman + spool-selected to the HTTP-only pair use
        // case so Save stays a pure form-edit action.
        if (mode == WriteMode.Spoolman && state.observedTagKind == ObservedTagKind.Vendor) {
            launchVendorUidOnlyPair(form, state.observedTagUid)
            return
        }
        when (mode) {
            WriteMode.RawNoUrl -> {
                launchRawWrite(form)
                return
            }
            WriteMode.Spoolman -> {
                val spoolId = form.selectedSpoolId ?: state.spoolman.selectedSpoolId
                if (spoolId == null) {
                    // Defensive — canWrite already gates on this. Should
                    // never fire in practice.
                    _effects.trySend(UiEffect.ShowSnackbar("Pick a spool or hit Save first."))
                    return
                }
                launchCreateAndPair(form, spoolId, isNewSpool = false)
            }
        }
    }

    private fun launchCreateAndPair(form: FormState, spoolId: Int, isNewSpool: Boolean) {
        _state.update { it.copy(activeFlow = ActiveFlow.WritingForPair) }
        writeJob = viewModelScope.launch {
            val materialName = resolveMaterialName(form.material, _customMaterial.value)
            val brandName = resolveBrandName(form.brand, _customBrand.value)
            val variantPart = form.variant?.trim().orEmpty()
            val derivedName = listOfNotNull(
                brandName.takeIf { it.isNotBlank() },
                materialName.takeIf { it.isNotBlank() },
                variantPart.takeIf { it.isNotBlank() },
            ).joinToString(" ").ifBlank { materialName }
            val input = CreateAndPairInput(
                spoolId = spoolId,
                isNewSpool = isNewSpool,
                form = form,
                newFilamentName = derivedName,
                newFilamentVendor = brandName.ifBlank { "Generic" },
                resolvedMaterialName = materialName.takeIf { it.isNotBlank() },
            )
            val result = withTimeoutOrNull(writeTimeoutMs) {
                createAndPair.invoke(input)
            } ?: CreateAndPairResult.Cancelled(reason = "timeout", spoolId = spoolId)
            applyWriteResult(result)
        }
    }

    private fun launchRawWrite(form: FormState) {
        _state.update { it.copy(activeFlow = ActiveFlow.WritingRaw) }
        writeJob = viewModelScope.launch {
            val materialName = resolveMaterialName(form.material, _customMaterial.value)
            val brandName = resolveBrandName(form.brand, _customBrand.value)
            val input = RawWriteInput(
                form = form,
                resolvedMaterialName = materialName.takeIf { it.isNotBlank() },
                newFilamentVendor = brandName.ifBlank { "Unknown" },
            )
            val result = withTimeoutOrNull(writeTimeoutMs) {
                rawWrite.invoke(input)
            } ?: RawWriteResult.Cancelled("timeout")
            applyRawWriteResult(result)
        }
    }

    private fun launchVendorUidOnlyPair(form: FormState, observedUid: com.spoolpainter.app.domain.primitives.CardUid?) {
        val observed = observedUid ?: form.cardUid
        if (observed == null) {
            _effects.trySend(UiEffect.ShowSnackbar("Tap the vendor tag again to capture its UID."))
            return
        }
        _state.update { it.copy(activeFlow = ActiveFlow.PairingVendorUidOnly) }
        writeJob = viewModelScope.launch {
            val materialName = resolveMaterialName(form.material, _customMaterial.value)
            val brandName = resolveBrandName(form.brand, _customBrand.value)
            val variantPart = form.variant?.trim().orEmpty()
            val derivedName = listOfNotNull(
                brandName.takeIf { it.isNotBlank() },
                materialName.takeIf { it.isNotBlank() },
                variantPart.takeIf { it.isNotBlank() },
            ).joinToString(" ").ifBlank { materialName }
            val input = VendorUidOnlyPairInput(
                form = form,
                newFilamentName = derivedName,
                newFilamentVendor = brandName.ifBlank { "Generic" },
                resolvedMaterialName = materialName.takeIf { it.isNotBlank() },
                observedUid = observed,
            )
            val result = withTimeoutOrNull(writeTimeoutMs) {
                vendorUidOnlyPair.invoke(input)
            } ?: VendorUidOnlyPairResult.Cancelled("timeout")
            applyVendorUidOnlyPairResult(result)
        }
    }

    fun onSpoolSelected(spool: SpoolmanSpool?) {
        android.util.Log.d("SpoolmanRepo", "onSpoolSelected: spool.id=${spool?.id}")
        if (spool == null) {
            // X on the spool dropdown clears ONLY the spool selection. The
            // user's form entries (material/brand/colour/temps/filament pick)
            // stay so they can keep editing and write a new spool against
            // them. Filament dropdown's X still does its own reset.
            //
            // Spool-scope fields ARE cleared though — those are meaningless
            // without a target spool: remaining_weight + the dirty-flag
            // snapshots (otherwise next save would PATCH a spool that no
            // longer exists / different spool). Display fallback in
            // MoreDetailsExpander then shows fullSpoolWeightG-derived
            // previews based on the still-selected filament.
            _state.update { current ->
                current.copy(
                    form = current.form.copy(
                        selectedSpoolId = null,
                        remainingWeightG = null,
                        prefilledRemainingWeightG = null,
                        prefilledPriceMajor = null,
                        prefilledEmptySpoolWeightG = null,
                    ),
                    spoolman = current.spoolman.copy(selectedSpoolId = null),
                )
            }
            selectionFormSnapshot = null
            return
        }
        // UI-54: deliberately NO same-id early return. Re-picking the spool
        // that is already selected re-derives the form from the current cache,
        // so a Spoolman-side edit can be pulled in without the
        // clear-then-reselect dance the user previously had to do.
        val currentState = _state.value
        val derived = FormMapping.fromSpoolman(
            spool = spool,
            currentUid = currentState.form.cardUid,
            rawWriteMode = currentState.form.rawWriteMode,
            uidSource = FormMapping.SpoolmanUidSource.FromCardUidsOrClear,
        )
        selectionFormSnapshot = derived
        _state.update { current ->
            current.copy(
                form = derived,
                spoolman = current.spoolman.copy(selectedSpoolId = spool.id),
                ambiguity = null,
                // User picked deliberately — drop the scan surfacing hints.
                scanSuggestedSpoolIds = emptyList(),
                scanSuggestedFilamentIds = emptyList(),
            )
        }
        _customMaterial.value = ""
        _customBrand.value = ""
    }

    /**
     * UI-54 — pull a Spoolman-side edit onto the already-selected spool.
     *
     * The form used to be a one-time snapshot taken at selection time:
     * [onPullToRefresh] refreshed the spool-list cache but nothing re-projected
     * the fresh record onto the form, so editing a spool in Spoolman's web UI
     * (say PLA to PETG) left the app showing the stale value however many times
     * the user pulled to refresh. Only clear-then-reselect worked.
     *
     * Hooked to the spools cache flow rather than to the pull gesture, so it
     * also covers the `MainActivity.onResume` refresh — edit in a browser tab,
     * switch back to the app, see the change.
     *
     * Re-derives ONLY an untouched form. If the user has changed any data field
     * since selecting, [selectionFormSnapshot] no longer matches and their
     * edits are left alone: a background refresh must never overwrite work in
     * progress. Same invariant the `prefilled*` stale-prefill snapshots protect
     * on the save path.
     */
    private fun reDeriveSelectedSpoolForm(spools: List<SpoolmanSpool>) {
        val current = _state.value
        val selectedId = current.spoolman.selectedSpoolId ?: return
        val snapshot = selectionFormSnapshot ?: run {
            android.util.Log.d(TAG_REDERIVE, "skip: no selection snapshot (spool $selectedId)")
            return
        }
        if (current.form.dataFingerprint() != snapshot.dataFingerprint()) {
            android.util.Log.d(
                TAG_REDERIVE,
                "skip: form edited by user, not clobbering (spool $selectedId)",
            )
            return
        }
        val fresh = spools.firstOrNull { it.id == selectedId } ?: run {
            android.util.Log.d(TAG_REDERIVE, "skip: spool $selectedId not in fresh cache")
            return
        }
        val reDerived = FormMapping.fromSpoolman(
            spool = fresh,
            currentUid = current.form.cardUid,
            rawWriteMode = current.form.rawWriteMode,
            // PreserveCurrent, not FromCardUidsOrClear: a background refresh
            // must not disturb the UID the user has in hand from a tap.
            uidSource = FormMapping.SpoolmanUidSource.PreserveCurrent,
        ).copy(
            moreDetailsExpanded = current.form.moreDetailsExpanded,
            weightMethod = current.form.weightMethod,
        )
        if (reDerived == current.form) {
            android.util.Log.d(TAG_REDERIVE, "no change: spool $selectedId already current")
            return
        }
        android.util.Log.d(
            TAG_REDERIVE,
            "RE-DERIVED spool $selectedId: " +
                "material ${current.form.material?.name}->${reDerived.material?.name} " +
                "color ${current.form.colorHex}->${reDerived.colorHex} " +
                "remaining ${current.form.remainingWeightG}->${reDerived.remainingWeightG} " +
                "variant ${current.form.variant}->${reDerived.variant}",
        )
        selectionFormSnapshot = reDerived
        _state.update { it.copy(form = reDerived) }
    }

    /**
     * UI-54 — the comparable, data-only projection of a form. Drops the view
     * state that a user can change without meaning "I edited this spool"
     * (which tag is in hand, raw-write toggle, expander open/closed, which
     * weight method is on screen), so toggling an expander doesn't suppress a
     * legitimate refresh.
     */
    private fun FormState.dataFingerprint(): FormState = copy(
        cardUid = null,
        rawWriteMode = false,
        moreDetailsExpanded = false,
        weightMethod = WeightMethod.Measured,
    )

    /**
     * U8-Δ-1 — pick a filament from the hidden "Filament ▾" expander. Mutex
     * with selectedSpoolId (Q-U8-7=A): on non-null pick, prefill the form
     * from the filament's metadata (material/vendor/color/temps + 5 expander
     * fields) and clear selectedSpoolId. Toggle states are preserved.
     */
    fun onFilamentSelected(filament: SpoolmanFilament?) {
        if (filament == null) {
            // UI-57: the X unlinks, it does NOT reset the form. Every typed and
            // prefilled value stays, mirroring onSpoolSelected(null) above. That
            // is what makes the "sister filament" flow work: pick an
            // already-configured filament, tap the X to unlink (which also drops
            // identityLocked so material/brand/colour become editable again),
            // change the colour, Save — and because no filament id is attached,
            // SaveToSpoolmanUseCase.resolveSpool takes its create branch, where
            // resolveOrCreateFilament matches on vendor + material + colour +
            // variant and therefore creates a NEW filament instead of reusing
            // the sister. Brand, density, diameter and weights carry over.
            //
            // The spool link goes too, and must: a selected spool *implies* its
            // filament (FormMapping.fromSpoolman sets selectedFilamentId from
            // spool.filament.id), so "filament unlinked, spool still linked" is
            // not a stable state — reDeriveSelectedSpoolForm would silently
            // re-link the filament on the next cache refresh. In the sister
            // flow this is a no-op, since picking a filament already cleared the
            // spool selection.
            //
            // Nothing else is cleared. The prefilled* snapshots are spool-scope
            // dirty flags read only behind `if (!isNewSpool)` in
            // SaveToSpoolmanUseCase, which a create-path save never reaches.
            // "Reset every field" now lives in onClearAll().
            _state.update { current ->
                current.copy(
                    form = current.form.copy(
                        selectedFilamentId = null,
                        selectedSpoolId = null,
                    ),
                    spoolman = current.spoolman.copy(selectedSpoolId = null),
                )
            }
            return
        }
        _state.update { current ->
            val prefilled = FormMapping.fromFilament(filament, current.form.rawWriteMode).copy(
                cardUid = current.form.cardUid,
                moreDetailsExpanded = current.form.moreDetailsExpanded,
            )
            current.copy(
                form = prefilled,
                spoolman = current.spoolman.copy(selectedSpoolId = null),
                ambiguity = null,
                // A filament is now selected — F3 (this filament's spools) takes
                // over the Spool picker's floated group; drop the scan hints.
                scanSuggestedSpoolIds = emptyList(),
                scanSuggestedFilamentIds = emptyList(),
            )
        }
        _customMaterial.value = ""
        _customBrand.value = ""
    }

    /**
     * UI-57 — "clear everything" from the header. This is the reset the filament
     * X used to perform as a side effect; it is now a deliberate action, because
     * once the X only unlinks there is otherwise no way back to a blank form.
     *
     * No confirmation dialog by design (the user clears often, so a prompt on
     * every clear is friction). Semantics are deliberately identical to the old
     * filament-X reset, which was proven in use: the two view-only toggles
     * survive so the user stays on the section they were looking at, and the
     * observed-tag / ambiguity state is dropped along with the form.
     */
    fun onClearAll() {
        _state.update { current ->
            current.copy(
                form = FormState(
                    rawWriteMode = current.form.rawWriteMode,
                    moreDetailsExpanded = current.form.moreDetailsExpanded,
                ),
                spoolman = current.spoolman.copy(selectedSpoolId = null),
                ambiguity = null,
                observedTagKind = ObservedTagKind.None,
                observedTagUid = null,
                scanSuggestedSpoolIds = emptyList(),
                scanSuggestedFilamentIds = emptyList(),
            )
        }
        _customMaterial.value = ""
        _customBrand.value = ""
    }

    fun onMoreDetailsToggled() {
        _state.update { it.copy(form = it.form.copy(moreDetailsExpanded = !it.form.moreDetailsExpanded)) }
    }

    fun onEmptySpoolWeightChanged(s: String) {
        if (s.isEmpty()) {
            _state.update { it.copy(form = it.form.copy(emptySpoolWeightG = null)) }
            return
        }
        val parsed = s.toFloatOrNull() ?: return
        _state.update { current ->
            val form = current.form
            // U13 — when measuredEntry was stashed because emptySpool was
            // unknown, commit remaining = measured − empty now that the
            // reference exists. Clamp ≥ 0 to avoid negative remaining when
            // the user typed something inconsistent.
            val updatedForm = if (form.weightMethod == WeightMethod.Measured && form.measuredEntry != null) {
                val rem = (form.measuredEntry - parsed).coerceAtLeast(0f)
                form.copy(
                    emptySpoolWeightG = parsed,
                    remainingWeightG = rem,
                )
            } else {
                form.copy(emptySpoolWeightG = parsed)
            }
            current.copy(form = updatedForm)
        }
    }
    fun onPriceChanged(s: String) = updateFloatField(s) { form, v -> form.copy(priceMajor = v) }
    fun onFullSpoolWeightChanged(s: String) = updateFloatField(s) { form, v -> form.copy(fullSpoolWeightG = v) }
    fun onDensityChanged(s: String) = updateFloatField(s) { form, v -> form.copy(densityGPerCm3 = v) }

    /**
     * U13 (Cluster A) — pick the weight measurement method. Switching the
     * method may drop a stashed measuredEntry (when leaving Measured before
     * emptySpool was set), but never clobbers committed remaining/empty
     * values.
     */
    fun onWeightMethodPicked(method: WeightMethod) {
        _state.update { current ->
            val form = current.form
            if (form.weightMethod == method) return@update current
            // Drop the transient on method change: it's a "I typed this in
            // Measured but emptySpool wasn't there yet" buffer, only relevant
            // while Measured is active.
            current.copy(form = form.copy(weightMethod = method, measuredEntry = null))
        }
    }

    /**
     * U13 (Cluster A) — write to the active weight method's underlying value.
     *
     *  - Active = Remaining: commit `remainingWeightG`. Empty input → null.
     *  - Active = Measured + emptySpool known: commit
     *    `remainingWeightG = measured − emptySpoolWeightG` (clamped ≥ 0).
     *    Skip negative back-solve to preserve mid-typing keystrokes
     *    ("9" before "950") — committing intermediate values triggers a
     *    recompute that resets the field's local string via `remember(value)`.
     *  - Active = Measured + emptySpool unknown: stash the user's measured
     *    value in `measuredEntry`. The display field stays as the source of
     *    truth; remaining is committed automatically when emptySpool resolves.
     */
    fun onActiveWeightChanged(s: String) {
        val current = _state.value.form
        when (current.weightMethod) {
            WeightMethod.Remaining -> {
                if (s.isEmpty()) {
                    _state.update { it.copy(form = it.form.copy(remainingWeightG = null)) }
                    return
                }
                val parsed = s.toFloatOrNull() ?: return
                _state.update { it.copy(form = it.form.copy(remainingWeightG = parsed)) }
            }
            WeightMethod.Measured -> {
                if (s.isEmpty()) {
                    _state.update {
                        it.copy(form = it.form.copy(remainingWeightG = null, measuredEntry = null))
                    }
                    return
                }
                val measured = s.toFloatOrNull() ?: return
                val empty = current.emptySpoolWeightG
                // Always stash exactly what the user typed in measuredEntry —
                // it's the active field's display source (see
                // activeWeightValueG in MainScreen), so committing it on every
                // keystroke keeps the field editable (no remember(value) reset)
                // and lets the user freely delete/retype. When the empty-spool
                // reference is known, also commit the derived remaining,
                // clamped ≥ 0 (a measured weight below the empty spool means an
                // empty spool, not a negative). Without a reference we can't
                // derive remaining, so leave it untouched.
                _state.update {
                    it.copy(
                        form = it.form.copy(
                            measuredEntry = measured,
                            remainingWeightG = if (empty != null) {
                                (measured - empty).coerceAtLeast(0f)
                            } else {
                                it.form.remainingWeightG
                            },
                        ),
                    )
                }
            }
        }
    }

    /**
     * Empty string -> null override; non-numeric -> keep prior value (no
     * surprise reset). Valid decimal -> Float.
     */
    private inline fun updateFloatField(input: String, set: (FormState, Float?) -> FormState) {
        if (input.isEmpty()) {
            _state.update { it.copy(form = set(it.form, null)) }
            return
        }
        val parsed = input.toFloatOrNull() ?: return
        _state.update { it.copy(form = set(it.form, parsed)) }
    }


    fun onMaterialPicked(value: Material?) {
        _state.update { it.copy(form = it.form.copy(material = value)) }
        if (value?.name != "Other") {
            _customMaterial.value = ""
        }
        // When picking a known material, also seed the temperature defaults
        // and the per-material density override (PLA 1.24 / ABS 1.04 / etc.).
        // If the preset has no density (e.g., "Other"), keep whatever the
        // user has already typed so we don't clobber a manual entry.
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
                        densityGPerCm3 = value.density ?: it.form.densityGPerCm3,
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

    private fun mapClassification(c: TagClassification?): ObservedTagKind? = when (c) {
        is TagClassification.Blank -> ObservedTagKind.Blank
        is TagClassification.OpenSpool -> ObservedTagKind.OpenSpool
        is TagClassification.Vendor -> ObservedTagKind.Vendor
        else -> null
    }

    private fun resolveMaterialName(material: Material?, custom: String): String {
        val raw = if (material?.name == "Other" && custom.isNotBlank()) custom
        else material?.name ?: ""
        if (raw.isBlank()) return raw
        val canonical = materials.value.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        return canonical?.name ?: raw
    }

    /**
     * UI-63 — returns the brand **exactly as the user chose it**. Whitespace is
     * the only thing normalised, and only because a trailing space would
     * otherwise render as a double space inside `derivedName`.
     *
     * This used to canonicalise `raw` against `brands.value`, so a hand-typed
     * "Tecbears" was rewritten to the preset spelling "TECBEARS" before being
     * written to the tag and into the Spoolman filament name — silently, and
     * differing from what the form still displayed on screen. Two reasons that
     * is gone rather than merely narrowed to Spoolman vendors:
     *
     * 1. Its stated justification was false. The old comment claimed Spoolman
     *    dedupes the vendor row case-insensitively, so only the derived filament
     *    name was at risk. Checked 2026-08-27: `vendor.name` is a plain
     *    `String(64)` with no unique constraint, index or collation, so Spoolman
     *    dedupes nothing. The cost was real and the benefit was imagined.
     * 2. Picking from the dropdown now yields an exact Spoolman vendor string
     *    (see `MaterialBrandRepository.mergeBrands`), so the case mismatch this
     *    guarded against can now only arise from someone deliberately walking
     *    past the dropdown and typing a variant under "Other". At that point
     *    what they typed is the answer.
     *
     * Existing vendor *records* are still never renamed: `resolveOrCreateVendor`
     * matches `ignoreCase = true` and reuses the row it finds, because renaming
     * would rewrite a record every other filament of that brand points at.
     */
    private fun resolveBrandName(brand: Brand?, custom: String): String =
        if (brand?.name == "Other" && custom.isNotBlank()) custom.trim()
        else brand?.name?.trim() ?: ""

    /**
     * U20 (UI-49) — score the current filament inventory against a decoded tag
     * payload and return the (spoolIds, filamentIds) to float to the top of the
     * pickers when the user next opens them, **in scorer-rank order** (best
     * first). Passive hints only: this NEVER changes any selection or flow.
     * Signals are material / brand / color only (no temps, per Q-U20-2). Empty
     * pair = nothing floated.
     */
    private fun computeScanSuggestions(payload: OpenSpoolPayload): Pair<List<Int>, List<Int>> {
        val query = SpoolMatchScorer.Query(
            material = payload.type,
            brand = payload.brand,
            colorHex = payload.colorHex,
            // Vendor tags decode their material modifier into subtype (e.g.
            // Bambu "Matte"); "Basic"/blank is the no-variant default and must
            // not match. Same rule FormMapping.fromOpenSpool uses.
            variant = payload.subtype.takeUnless { it == "Basic" || it.isBlank() },
        )
        // Rank order (best match first). The pickers float in exactly this order.
        val filamentIds = SpoolMatchScorer.suggestedFilamentIds(query, matchCandidates(filaments.value))
        if (filamentIds.isEmpty()) return emptyList<Int>() to emptyList()
        // A suggested filament implies its unarchived spools are suggested too;
        // order the spools by their filament's rank so the best match floats
        // first there as well.
        val rankByFilament = filamentIds.withIndex().associate { (i, id) -> id to i }
        val spoolIds = spoolman.spools.value
            .filterNot { it.archived }
            .filter { it.filament.id in rankByFilament }
            .sortedBy { rankByFilament[it.filament.id] }
            .mapNotNull { it.id }
        return spoolIds to filamentIds
    }

    /**
     * Reduce the Spoolman inventory to what the scorer ranks on. Shared by both
     * float triggers (scan and form) so they can never drift on how a filament
     * becomes a [SpoolMatchScorer.Candidate] — notably that Spoolman keeps the
     * variant as a JSON-encoded string inside `extra`.
     */
    private fun matchCandidates(inventory: List<SpoolmanFilament>): List<SpoolMatchScorer.Candidate> =
        inventory.map { f ->
            SpoolMatchScorer.Candidate(
                filamentId = f.id,
                material = f.material,
                brand = f.vendor?.name,
                colorHex = f.color_hex,
                variant = FormMapping.decodeExtraVariant(f.extra?.get("variant")),
            )
        }

    private fun applyResult(result: ReadAndPairResult) {
        when (result) {
            is ReadAndPairResult.Success.PrefillFromSpoolman -> {
                val stateNow = _state.value
                val mapped = FormMapping.fromSpoolman(
                    result.spool,
                    result.uid,
                    stateNow.form.rawWriteMode,
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
                // UI-54 — a spool selected by a tag read is refreshable too.
                // This is the exact scenario in the report: read a tag, then
                // edit that spool in Spoolman.
                selectionFormSnapshot = merged
                _state.update { current ->
                    current.copy(
                        form = merged,
                        spoolman = current.spoolman.copy(selectedSpoolId = result.spool.id),
                        ambiguity = null,
                        activeFlow = ActiveFlow.Idle,
                        // Paired read resolved a concrete spool — no need to
                        // suggest anything; clear any prior scan hints.
                        scanSuggestedSpoolIds = emptyList(),
                        scanSuggestedFilamentIds = emptyList(),
                    )
                }
                _customMaterial.value = ""
                _customBrand.value = ""
            }
            is ReadAndPairResult.Success.PrefillFromTag -> {
                // Unpaired OpenSpool tag: score the inventory so the pickers can
                // float the closest matches when opened (passive; no auto-select).
                val (spoolIds, filamentIds) = computeScanSuggestions(result.payload)
                _state.update { current ->
                    current.copy(
                        form = FormMapping.fromOpenSpool(result.uid, result.payload, current.form.rawWriteMode),
                        spoolman = current.spoolman.copy(selectedSpoolId = null),
                        ambiguity = null,
                        activeFlow = ActiveFlow.Idle,
                        // Tag carried OpenSpool data, not vendor-encoded.
                        observedTagKind = ObservedTagKind.OpenSpool,
                        observedTagUid = result.uid,
                        scanSuggestedSpoolIds = spoolIds,
                        scanSuggestedFilamentIds = filamentIds,
                    )
                }
                _customMaterial.value = ""
                _customBrand.value = ""
            }
            is ReadAndPairResult.Success.BlankForm -> {
                // v1 parity: a Read on a blank tag clears NOTHING — keep the
                // form (material/brand/colour/temps), keep the filament
                // selection if any, keep the spool selection if any. Only
                // update the cardUid so a subsequent Save & Write knows which
                // tag to write to.
                //
                // 2026-06-06 fix: Read of an unpaired VENDOR tag IS different.
                // The user is signalling "I want to map this tag" — the
                // previously-selected spool is stale (it doesn't own this
                // UID). Clear it so the user picks a target deliberately.
                val vendor = result.classification as? TagClassification.Vendor
                val isVendor = vendor != null
                val parsedHint = vendor?.parsedHint
                // Unpaired vendor tag with a decoded payload: score the inventory
                // for picker surfacing (passive; no auto-select). No hint / a plain
                // blank tag carries no metadata, so nothing to suggest.
                val (scanSpoolIds, scanFilamentIds) = if (parsedHint != null) {
                    computeScanSuggestions(parsedHint)
                } else {
                    emptyList<Int>() to emptyList()
                }
                _state.update { current ->
                    val nextForm = if (parsedHint != null) {
                        // Vendor tag with a successful Bambu/Snapmaker parse:
                        // prefill the form from the parsed payload so the user
                        // sees the chip's real metadata. selectedSpoolId stays
                        // null — the user picks the Spoolman target deliberately,
                        // then Save+Map-tag pairs it via U13's vendor write path.
                        FormMapping.fromOpenSpool(result.uid, parsedHint, current.form.rawWriteMode)
                    } else {
                        current.form.copy(
                            cardUid = result.uid,
                            selectedSpoolId = if (isVendor) null else current.form.selectedSpoolId,
                        )
                    }
                    current.copy(
                        form = nextForm,
                        spoolman = if (isVendor) {
                            current.spoolman.copy(selectedSpoolId = null)
                        } else current.spoolman,
                        ambiguity = null,
                        activeFlow = ActiveFlow.Idle,
                        scanSuggestedSpoolIds = scanSpoolIds,
                        scanSuggestedFilamentIds = scanFilamentIds,
                    )
                }
                if (parsedHint != null) {
                    _customMaterial.value = ""
                    _customBrand.value = ""
                }
                when {
                    !isVendor ->
                        _effects.trySend(UiEffect.ShowSnackbar("Blank tag detected."))
                    // Vendor chip but the decode came back empty. The dominant
                    // cause is a short tap: vendor chips (esp. Snapmaker, whose
                    // RSA signature lives in the last sectors) need the whole
                    // tag read, and the OS "tag detected" buzz fires long before
                    // that finishes — so lifting at the buzz aborts the read.
                    // We can't tell WHICH vendor failed here (the decode that
                    // would name it is the thing that failed), so a "hold and
                    // retry" message is the honest, universally-correct cue —
                    // it fixes the common partial-read case and never
                    // misdirects (a Snapmaker chip needs no key, so a
                    // "configure a key" hint would be wrong for it).
                    parsedHint == null ->
                        _effects.trySend(
                            UiEffect.ShowSnackbar("Couldn't read the tag. Hold still and press Read again."),
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
                    val filamentId = current.spoolman.spools
                        .firstOrNull { it.id == result.spoolId }?.filament?.id
                    current.copy(
                        form = current.form.copy(
                            cardUid = result.uid,
                            selectedSpoolId = result.spoolId,
                            selectedFilamentId = filamentId ?: current.form.selectedFilamentId,
                        ),
                        spoolman = current.spoolman.copy(selectedSpoolId = result.spoolId),
                        observedTagKind = ObservedTagKind.None,
                        observedTagUid = null,
                        activeFlow = ActiveFlow.PromptingPairAnother(
                            spoolId = result.spoolId,
                            isVendorPair = result.isVendorPair,
                        ),
                    )
                }
            }
            is CreateAndPairResult.VerifyFailed -> {
                // Spool exists in Spoolman before the write tap; keep the
                // selection so retry doesn't re-fill the form.
                _state.update { current ->
                    current.copy(
                        activeFlow = ActiveFlow.Idle,
                        form = current.form.copy(selectedSpoolId = result.spoolId),
                        spoolman = current.spoolman.copy(selectedSpoolId = result.spoolId),
                    )
                }
                _effects.trySend(
                    UiEffect.ShowSnackbar("Tag write failed. Try again."),
                )
            }
            is CreateAndPairResult.SpoolmanFailed -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar(humanReadable(result.outcome)))
            }
            is CreateAndPairResult.NfcFailed -> {
                // Write never creates or deletes a spool — Save did that on a
                // separate tap. Pin the spool/filament selection so a retry
                // Write appends to the existing record instead of duplicating.
                _state.update { current ->
                    val pinSpoolId = result.spoolId
                    val filamentId = pinSpoolId?.let { id ->
                        current.spoolman.spools.firstOrNull { it.id == id }?.filament?.id
                    }
                    current.copy(
                        activeFlow = ActiveFlow.Idle,
                        form = current.form.copy(
                            selectedSpoolId = pinSpoolId ?: current.form.selectedSpoolId,
                            selectedFilamentId = filamentId ?: current.form.selectedFilamentId,
                        ),
                        spoolman = current.spoolman.copy(
                            selectedSpoolId = pinSpoolId ?: current.spoolman.selectedSpoolId,
                        ),
                    )
                }
                val msg = when {
                    result.reason.contains("vendor-tag", ignoreCase = true) ->
                        "Vendor tag. Write blocked."
                    // The UID was appended to the spool before the write
                    // outcome was decided (CreateAndPairUseCase step 3), so
                    // the tag IS mapped by serial even though its payload
                    // didn't fit. Say so instead of a flat failure.
                    result.reason.contains("too small", ignoreCase = true) ->
                        "Paired only. This tag is too small to write full data."
                    else ->
                        "Tag write failed. Try again."
                }
                _effects.trySend(UiEffect.ShowSnackbar(msg))
            }
            is CreateAndPairResult.Cancelled -> {
                // Write never deletes a spool — keep it and pin selection so a
                // retry Write appends to the existing record.
                _state.update { current ->
                    val pinSpoolId = result.spoolId
                    val filamentId = pinSpoolId?.let { id ->
                        current.spoolman.spools.firstOrNull { it.id == id }?.filament?.id
                    }
                    current.copy(
                        activeFlow = ActiveFlow.Idle,
                        form = current.form.copy(
                            selectedSpoolId = pinSpoolId ?: current.form.selectedSpoolId,
                            selectedFilamentId = filamentId ?: current.form.selectedFilamentId,
                        ),
                        spoolman = current.spoolman.copy(
                            selectedSpoolId = pinSpoolId ?: current.spoolman.selectedSpoolId,
                        ),
                    )
                }
                viewModelScope.launch { nfc.disarm() }
                // Move-on-bind decline already gave the user explicit choice
                // via the RepairConfirmSheet; emitting "No tag tapped" here is
                // misleading. Only show the snackbar for the genuine timeout
                // / no-tap path.
                if (!result.reason.startsWith("repair declined", ignoreCase = true)) {
                    _effects.trySend(UiEffect.ShowSnackbar("No tag tapped. Try again."))
                }
            }
        }
    }

    /**
     * U13 — apply [SaveToSpoolmanResult] back to UI state. On success, auto-
     * select the spool in the dropdown + pin form selection so the next Write
     * tap appends to it. Snackbar copy varies on new vs existing.
     */
    private fun applySaveResult(result: SaveToSpoolmanResult) {
        when (result) {
            is SaveToSpoolmanResult.Success.Saved -> {
                _state.update { current ->
                    val filamentId = current.spoolman.spools
                        .firstOrNull { it.id == result.spoolId }?.filament?.id
                    val updatedForm = current.form.copy(
                        selectedSpoolId = result.spoolId,
                        selectedFilamentId = filamentId ?: current.form.selectedFilamentId,
                        // Refresh prefilled snapshots to current values so a
                        // follow-up Save with no further edits is a no-op.
                        prefilledRemainingWeightG = current.form.remainingWeightG,
                        prefilledPriceMajor = current.form.priceMajor,
                        prefilledEmptySpoolWeightG = current.form.emptySpoolWeightG,
                    )
                    current.copy(
                        form = updatedForm,
                        spoolman = current.spoolman.copy(selectedSpoolId = result.spoolId),
                    )
                }
                val isVendor = _state.value.observedTagKind == ObservedTagKind.Vendor
                val msg = when {
                    result.isNewSpool && isVendor ->
                        "Saved spool #${result.spoolId}. Finish with Map tag."
                    result.isNewSpool ->
                        "Saved spool #${result.spoolId}. Use Write to finish."
                    else ->
                        "Updated spool #${result.spoolId}."
                }
                _effects.trySend(UiEffect.ShowSnackbar(msg))
            }
            is SaveToSpoolmanResult.NoChanges -> {
                // Save button should have been greyed; this only fires on a race.
                android.util.Log.d("SpoolmanRepo", "Save no-op for spool #${result.spoolId}")
            }
            is SaveToSpoolmanResult.Failed -> {
                _effects.trySend(UiEffect.ShowSnackbar(humanReadable(result.outcome)))
            }
            SaveToSpoolmanResult.UrlNotConfigured -> {
                _effects.trySend(UiEffect.ShowSnackbar("Configure Spoolman in Settings."))
            }
        }
    }

    /**
     * U13 §11 — sheet's "Pair another" button. Behaves as a toggle when a
     * second-tag write is in flight: tapping during [ActiveFlow.WritingSecondTag]
     * cancels the writeJob + disarms NFC + returns to [ActiveFlow.PromptingPairAnother]
     * (the sheet stays visible — user is still inside the pair-another flow).
     */
    fun onPairAnotherTagAccepted() {
        val state = _state.value
        val flow = state.activeFlow
        if (flow is ActiveFlow.WritingSecondTag) {
            // Cancel branch — same target spool, return to prompt state.
            writeJob?.cancel()
            writeJob = null
            viewModelScope.launch { nfc.disarm() }
            _state.update { current ->
                current.copy(
                    activeFlow = ActiveFlow.PromptingPairAnother(
                        spoolId = flow.spoolId,
                        isVendorPair = false,
                    ),
                )
            }
            return
        }
        val current = flow as? ActiveFlow.PromptingPairAnother ?: return
        val spoolId = current.spoolId
        _state.update { it.copy(activeFlow = ActiveFlow.WritingSecondTag(spoolId)) }
        writeJob = viewModelScope.launch {
            val result = withTimeoutOrNull(writeTimeoutMs) {
                twoTag.invoke(TwoTagInput(spoolId))
            } ?: TwoTagResult.Cancelled("timeout")
            applyTwoTagResult(result)
        }
    }

    fun onPairAnotherTagDismissed() {
        val current = _state.value.activeFlow as? ActiveFlow.PromptingPairAnother ?: return
        _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
        _effects.trySend(
            UiEffect.ShowSnackbar(pairedMessage(current.spoolId, written = !current.isVendorPair)),
        )
    }

    fun onRepairResult(confirm: Boolean) {
        confirmer.submitResult(confirm)
    }

    private fun applyTwoTagResult(result: TwoTagResult) {
        when (result) {
            is TwoTagResult.Success.SecondTagPaired -> {
                val spoolId = _state.value.form.selectedSpoolId
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                val msg = spoolId?.let { pairedMessage(it, written = true) }
                    ?: "Tag written and paired."
                _effects.trySend(UiEffect.ShowSnackbar(msg))
            }
            is TwoTagResult.VendorTagRejected -> {
                // Second tag is a vendor tag — re-route to the vendor
                // UID-only pair flow so we link its UID without writing NDEF.
                // Goes straight to Idle on success (no PromptingPairAnother
                // — we don't want to recursively prompt-pair-another forever).
                val state = _state.value
                val targetSpoolId = state.form.selectedSpoolId
                if (targetSpoolId == null) {
                    _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                    _effects.trySend(UiEffect.ShowSnackbar("Vendor tag. Pick a spool first."))
                } else {
                    _state.update {
                        it.copy(
                            activeFlow = ActiveFlow.PairingVendorUidOnly,
                            observedTagKind = ObservedTagKind.Vendor,
                            observedTagUid = result.uid,
                        )
                    }
                    viewModelScope.launch {
                        val materialName = resolveMaterialName(state.form.material, _customMaterial.value)
                        val brandName = resolveBrandName(state.form.brand, _customBrand.value)
                        val variantPart = state.form.variant?.trim().orEmpty()
                        val derivedName = listOfNotNull(
                            brandName.takeIf { it.isNotBlank() },
                            materialName.takeIf { it.isNotBlank() },
                            variantPart.takeIf { it.isNotBlank() },
                        ).joinToString(" ").ifBlank { materialName }
                        val input = VendorUidOnlyPairInput(
                            form = state.form,
                            newFilamentName = derivedName,
                            newFilamentVendor = brandName.ifBlank { "Generic" },
                            resolvedMaterialName = materialName.takeIf { it.isNotBlank() },
                            observedUid = result.uid,
                        )
                        val r = withTimeoutOrNull(writeTimeoutMs) {
                            vendorUidOnlyPair.invoke(input)
                        } ?: VendorUidOnlyPairResult.Cancelled("timeout")
                        when (r) {
                            is VendorUidOnlyPairResult.Success.UidPaired -> {
                                _state.update {
                                    it.copy(
                                        activeFlow = ActiveFlow.Idle,
                                        observedTagKind = ObservedTagKind.None,
                                        observedTagUid = null,
                                    )
                                }
                                _effects.trySend(
                                    UiEffect.ShowSnackbar(pairedMessage(r.spoolId, written = false)),
                                )
                            }
                            else -> applyVendorUidOnlyPairResult(r)
                        }
                    }
                }
            }
            is TwoTagResult.VerifyFailed -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(
                    UiEffect.ShowSnackbar("Couldn't write to second tag. Tap Write to retry."),
                )
            }
            is TwoTagResult.SpoolmanFailed -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar(humanReadable(result.outcome)))
            }
            is TwoTagResult.MoveOnBindPartial -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(
                    UiEffect.ShowSnackbar(
                        "Couldn't finish moving the tag. Spool #${result.partiallyModifiedSpoolId} already released the tag. Re-add it in Spoolman if needed.",
                    ),
                )
            }
            is TwoTagResult.NfcFailed -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(
                    UiEffect.ShowSnackbar("Couldn't write to second tag. Tap Write to retry."),
                )
            }
            is TwoTagResult.Cancelled -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                // Suppress on explicit user decline (RepairConfirmSheet
                // Cancel). Emit only for genuine timeouts / unknown reasons.
                if (!result.reason.startsWith("repair declined", ignoreCase = true)) {
                    _effects.trySend(
                        UiEffect.ShowSnackbar("No second tag tapped. Tap Write to retry."),
                    )
                }
            }
        }
    }

    private fun applyRawWriteResult(result: RawWriteResult) {
        when (result) {
            is RawWriteResult.Success.Written -> {
                _state.update {
                    it.copy(
                        form = it.form.copy(cardUid = result.uid),
                        activeFlow = ActiveFlow.Idle,
                    )
                }
                _effects.trySend(UiEffect.ShowSnackbar("Tag written"))
            }
            is RawWriteResult.VendorTagRejected -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar("Vendor tag. Content unreadable."))
            }
            is RawWriteResult.VerifyFailed -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar("Couldn't write to tag. Try again."))
            }
            is RawWriteResult.NfcFailed -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar("Couldn't write to tag. Try again."))
            }
            is RawWriteResult.Cancelled -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                viewModelScope.launch { nfc.disarm() }
                _effects.trySend(UiEffect.ShowSnackbar("No tag tapped. Try again."))
            }
        }
    }

    private fun applyVendorUidOnlyPairResult(result: VendorUidOnlyPairResult) {
        when (result) {
            is VendorUidOnlyPairResult.Success.UidPaired -> {
                // Symmetric with create-and-pair: PairAnotherTagSheet fires so
                // the user can pair another tag (vendor or blank) with the
                // same spool. observedTagKind cleared so the second-tag flow
                // routes through the right path based on what's tapped next.
                _state.update { current ->
                    val filamentId = current.spoolman.spools
                        .firstOrNull { it.id == result.spoolId }?.filament?.id
                    current.copy(
                        form = current.form.copy(
                            cardUid = result.uid,
                            selectedSpoolId = result.spoolId,
                            selectedFilamentId = filamentId ?: current.form.selectedFilamentId,
                        ),
                        spoolman = current.spoolman.copy(selectedSpoolId = result.spoolId),
                        observedTagKind = ObservedTagKind.None,
                        observedTagUid = null,
                        activeFlow = ActiveFlow.PromptingPairAnother(
                            spoolId = result.spoolId,
                            isVendorPair = true,
                        ),
                    )
                }
            }
            is VendorUidOnlyPairResult.SpoolmanFailed -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(UiEffect.ShowSnackbar(humanReadable(result.outcome)))
            }
            is VendorUidOnlyPairResult.MoveOnBindPartial -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                _effects.trySend(
                    UiEffect.ShowSnackbar(
                        "Couldn't finish moving the tag. Spool #${result.partiallyModifiedSpoolId} already released the tag; please re-add it in Spoolman if needed.",
                    ),
                )
            }
            is VendorUidOnlyPairResult.Cancelled -> {
                _state.update { it.copy(activeFlow = ActiveFlow.Idle) }
                // Suppress on explicit user decline (RepairConfirmSheet Cancel)
                // — same UI-12 logic as create-and-pair.
                if (!result.reason.startsWith("repair declined", ignoreCase = true) &&
                    result.reason != "timeout"
                ) {
                    _effects.trySend(UiEffect.ShowSnackbar("Cancelled (${result.reason})"))
                } else if (result.reason == "timeout") {
                    _effects.trySend(UiEffect.ShowSnackbar("No tag tapped. Try again."))
                }
            }
        }
    }

    /**
     * UI-44 — how many tags are paired with [spoolId], read from Spoolman's
     * `extra.card_uids`. Sourced from the repo StateFlow (`spoolman.spools`),
     * which [SpoolmanRepository.appendCardUidToSpool] updates synchronously via
     * `replaceSpoolInCache` right after the PATCH — so it reflects the UID we
     * just appended, unlike the async VM state mirror. Returns 0 if unknown.
     */
    private fun pairedTagCount(spoolId: Int): Int {
        val spool = spoolman.spools.value.firstOrNull { it.id == spoolId } ?: return 0
        return ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "").size
    }

    /** End-of-pairing snackbar: confirmation + the true total tag count for
     *  [spoolId]. [written] is false for vendor tags, which are UID-mapped
     *  only (no NDEF payload written), so the prefix must not claim a write.
     *  The count clause is dropped when unknown (0) so we never say "0 tags". */
    private fun pairedMessage(spoolId: Int, written: Boolean): String {
        val prefix = if (written) "Tag written and paired." else "Vendor tag linked."
        val count = pairedTagCount(spoolId)
        return if (count < 1) {
            prefix
        } else {
            "$prefix This spool now has $count ${if (count == 1) "tag" else "tags"}."
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
        const val AMBIENT_HINT_COOLDOWN_MS: Long = 15_000L

        // UI-54 — its own logcat tag so the refresh re-derive decision can be
        // watched in isolation during install-gate testing. Stripped from
        // release by the -assumenosideeffects Log rule (NFR-5).
        const val TAG_REDERIVE = "SpoolRederive"
    }
}

/**
 * U24 (UI-59) — the slice of [MainUiState] that can change which filaments the
 * picker floats. Projected out and de-duplicated so an unrelated state update
 * (or a keystroke that lands on the same value) doesn't re-score the inventory.
 */
private data class FormSuggestionSignals(
    val scanSuggestedFilamentIds: List<Int>,
    val hasSelection: Boolean,
    val material: String?,
    val brand: String?,
    val colorHex: String?,
    val variant: String?,
)

private data class SortProjection(
    val spoolKey: com.spoolpainter.app.data.local.SpoolSortKey,
    val spoolDir: com.spoolpainter.app.data.local.SortDirection,
    val filamentKey: com.spoolpainter.app.data.local.FilamentSortKey,
    val filamentDir: com.spoolpainter.app.data.local.SortDirection,
    val priceSuffix: String,
)
