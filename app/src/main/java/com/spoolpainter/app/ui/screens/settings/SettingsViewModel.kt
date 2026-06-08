package com.spoolpainter.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoolpainter.app.data.local.Currency
import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SettingsRepository
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.data.local.SpoolSortKey
import com.spoolpainter.app.data.local.ThemeOverride
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.data.remote.spoolman.UrlNotConfiguredException
import com.spoolpainter.app.hardware.nfc.NfcReadLog
import com.spoolpainter.app.ui.common.UiEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val spoolman: SpoolmanRepository,
    private val nfcReadLog: NfcReadLog,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = settings.settings
        .map {
            SettingsUiState(
                url = it.url,
                spoolSortKey = it.spoolSortKey,
                spoolSortDirection = it.spoolSortDirection,
                filamentSortKey = it.filamentSortKey,
                filamentSortDirection = it.filamentSortDirection,
                currency = it.currency,
                bambuSalt = it.bambuSalt,
                crealitySalt = it.crealitySalt,
                crealityEncKey = it.crealityEncKey,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun onSpoolSortKeyChanged(key: SpoolSortKey) {
        viewModelScope.launch { settings.setSpoolSortKey(key) }
    }

    fun onSpoolSortDirectionChanged(direction: SortDirection) {
        viewModelScope.launch { settings.setSpoolSortDirection(direction) }
    }

    fun onFilamentSortKeyChanged(key: FilamentSortKey) {
        viewModelScope.launch { settings.setFilamentSortKey(key) }
    }

    fun onFilamentSortDirectionChanged(direction: SortDirection) {
        viewModelScope.launch { settings.setFilamentSortDirection(direction) }
    }

    fun onCurrencyChanged(currency: Currency) {
        viewModelScope.launch { settings.setCurrency(currency) }
    }

    val themeOverride: StateFlow<ThemeOverride> = settings.settings
        .map { it.themeOverride }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeOverride.Dark)

    fun onThemeToggled() {
        viewModelScope.launch {
            val next = if (themeOverride.value == ThemeOverride.Dark) {
                ThemeOverride.Light
            } else {
                ThemeOverride.Dark
            }
            settings.setThemeOverride(next)
        }
    }

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    fun onUrlSaved(url: String) {
        viewModelScope.launch {
            val normalised = normaliseUrl(url)
            settings.setUrl(normalised)
            _effects.trySend(UiEffect.ShowSnackbar("URL saved"))
        }
    }

    fun onBambuSaltSaved(salt: String) {
        viewModelScope.launch {
            settings.setBambuSalt(salt.trim())
            _effects.trySend(UiEffect.ShowSnackbar("Bambu salt saved"))
        }
    }

    fun onCrealitySaltSaved(salt: String) {
        viewModelScope.launch {
            settings.setCrealitySalt(salt.trim())
            _effects.trySend(UiEffect.ShowSnackbar("Creality tag key saved"))
        }
    }

    fun onCrealityEncKeySaved(key: String) {
        viewModelScope.launch {
            settings.setCrealityEncKey(key.trim())
            _effects.trySend(UiEffect.ShowSnackbar("Creality encryption key saved"))
        }
    }

    private fun normaliseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    /** Snapshot of the recent NFC reads, paste-friendly. */
    fun buildNfcShareText(formUrl: String? = null): String = nfcReadLog.renderShareText(formUrl)

    /** True when there's at least one read in the buffer. */
    fun hasNfcReads(): Boolean = !nfcReadLog.isEmpty()

    fun onRefreshTapped() {
        viewModelScope.launch {
            val message = when (val outcome = spoolman.refresh()) {
                is SpoolmanOutcome.Success -> "Refreshed spool list"
                is SpoolmanOutcome.HttpError -> "HTTP ${outcome.code}: ${outcome.message}"
                is SpoolmanOutcome.NetworkError -> if (outcome.cause is UrlNotConfiguredException) {
                    "Save a URL first"
                } else {
                    "Network error: ${outcome.cause.message ?: outcome.cause::class.simpleName}"
                }
                is SpoolmanOutcome.ParseError -> "Could not parse Spoolman response"
            }
            _effects.trySend(UiEffect.ShowSnackbar(message))
        }
    }
}
