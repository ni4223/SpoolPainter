package com.spoolpainter.app.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoolpainter.app.data.local.SettingsRepository
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.data.remote.spoolman.UrlNotConfiguredException
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.usecases.ReadAndPairResult
import com.spoolpainter.app.domain.usecases.ReadAndPairUseCase
import com.spoolpainter.app.hardware.nfc.NfcRepository
import com.spoolpainter.app.ui.common.UiEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
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
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    private var readJob: Job? = null

    // 10 s wall-clock fuse: if no tap arrives within this window the read is cancelled and the
    // hint clears. Configurable via constructor for tests; production install always uses 10s.
    internal val readTimeoutMs: Long = READ_TIMEOUT_MS_DEFAULT

    init {
        viewModelScope.launch {
            nfc.state.collect { value ->
                _state.update { it.copy(nfc = value) }
            }
        }
        viewModelScope.launch {
            // Ambient UID surfacing — show the most recently tapped UID even when no Read is armed.
            // The Read use-case still owns flow logic; this just keeps the UID row in sync with
            // NfcRepository.lastSeenTag so an unarmed tap is visible to the user (S-1.1).
            nfc.lastSeenTag
                .map { it?.uid }
                .distinctUntilChanged()
                .collect { uid ->
                    if (uid != null) {
                        _state.update { it.copy(form = it.form.copy(cardUid = uid)) }
                    }
                }
        }
        viewModelScope.launch {
            spoolman.spools.collect { value ->
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
    }

    fun onReadTapped() {
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

    private companion object {
        const val READ_TIMEOUT_MS_DEFAULT: Long = 10_000L
    }

    fun onSpoolSelected(spool: SpoolmanSpool?) {
        if (spool == null) {
            // Clear selection clears UID too (Q-U5-7 revised 2026-05-25).
            _state.update { current ->
                current.copy(
                    form = FormState(rawWriteMode = current.form.rawWriteMode),
                    spoolman = current.spoolman.copy(selectedSpoolId = null),
                    ambiguity = null,
                )
            }
            return
        }
        if (spool.id == _state.value.form.selectedSpoolId) return
        _state.update { current ->
            current.copy(
                // Manual dropdown selection: take UID from the spool's lot_nr; clear if absent
                // (Q-U5-7 revised 2026-05-25 — UID row reflects "the UID we'd act on right now").
                form = FormMapping.fromSpoolman(
                    spool = spool,
                    currentUid = current.form.cardUid,
                    rawWriteMode = current.form.rawWriteMode,
                    uidSource = FormMapping.SpoolmanUidSource.FromLotNrOrClear,
                ),
                spoolman = current.spoolman.copy(selectedSpoolId = spool.id),
                ambiguity = null,
            )
        }
    }

    fun onSettingsTapped() {
        _effects.trySend(UiEffect.Navigate("settings"))
    }

    private fun applyResult(result: ReadAndPairResult) {
        when (result) {
            is ReadAndPairResult.Success.PrefillFromSpoolman -> {
                _state.update { current ->
                    current.copy(
                        form = FormMapping.fromSpoolman(result.spool, result.uid, current.form.rawWriteMode),
                        spoolman = current.spoolman.copy(selectedSpoolId = result.spool.id),
                        ambiguity = null,
                        activeFlow = ActiveFlow.Idle,
                    )
                }
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
            }
            is ReadAndPairResult.Success.BlankForm -> {
                _state.update { current ->
                    current.copy(
                        form = FormMapping.blankForm(result.uid, current.form.rawWriteMode),
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

    private fun humanReadable(outcome: SpoolmanOutcome<*>): String = when (outcome) {
        is SpoolmanOutcome.Success<*> -> "Spoolman call succeeded unexpectedly"
        is SpoolmanOutcome.HttpError -> "Spoolman returned ${outcome.code}: ${outcome.message}"
        is SpoolmanOutcome.NetworkError -> {
            if (outcome.cause is UrlNotConfiguredException) {
                // Should have been short-circuited by the use-case (BR-U5-RP-7); defensive copy.
                "Spoolman URL not configured"
            } else {
                "Could not reach Spoolman: ${outcome.cause.message ?: outcome.cause::class.simpleName}"
            }
        }
        is SpoolmanOutcome.ParseError -> "Spoolman response could not be parsed"
    }
}
