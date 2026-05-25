package com.spoolpainter.app.support

import com.spoolpainter.app.data.local.Settings
import com.spoolpainter.app.data.local.SettingsRepository
import com.spoolpainter.app.data.local.SortOrder
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

    override suspend fun setSortOrder(order: SortOrder) {
        _settings.update { it.copy(sortOrder = order) }
    }

    override suspend fun setThemeOverride(theme: ThemeOverride) {
        _settings.update { it.copy(themeOverride = theme) }
    }

    fun pushSettings(value: Settings) {
        _settings.value = value
    }
}
