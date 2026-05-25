package com.spoolpainter.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoolpainter.app.data.local.SettingsRepository
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.data.remote.spoolman.UrlNotConfiguredException
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
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = settings.settings
        .map { SettingsUiState(it.url, it.sortOrder, it.themeOverride) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    fun onUrlSaved(url: String) {
        viewModelScope.launch {
            val normalised = normaliseUrl(url)
            settings.setUrl(normalised)
            _effects.trySend(UiEffect.ShowSnackbar("URL saved"))
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

    fun onTestConnectionTapped() {
        viewModelScope.launch {
            val message = when (val outcome = spoolman.probe()) {
                is SpoolmanOutcome.Success -> "Connected to Spoolman"
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
