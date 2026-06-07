package com.spoolpainter.app.support

import com.spoolpainter.app.data.local.Currency
import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.Settings
import com.spoolpainter.app.data.local.SettingsRepository
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.data.local.SpoolSortKey
import com.spoolpainter.app.data.local.ThemeOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {

    private val _settings = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = _settings.asStateFlow()

    override suspend fun setUrl(url: String) {
        _settings.update { it.copy(url = url) }
    }

    override suspend fun setSpoolSortKey(key: SpoolSortKey) {
        _settings.update { it.copy(spoolSortKey = key) }
    }

    override suspend fun setSpoolSortDirection(direction: SortDirection) {
        _settings.update { it.copy(spoolSortDirection = direction) }
    }

    override suspend fun setFilamentSortKey(key: FilamentSortKey) {
        _settings.update { it.copy(filamentSortKey = key) }
    }

    override suspend fun setFilamentSortDirection(direction: SortDirection) {
        _settings.update { it.copy(filamentSortDirection = direction) }
    }

    override suspend fun setThemeOverride(theme: ThemeOverride) {
        _settings.update { it.copy(themeOverride = theme) }
    }

    override suspend fun setCurrency(currency: Currency) {
        _settings.update { it.copy(currency = currency) }
    }

    override suspend fun setBambuSalt(salt: String) {
        _settings.update { it.copy(bambuSalt = salt) }
    }

    fun pushSettings(value: Settings) {
        _settings.value = value
    }
}
