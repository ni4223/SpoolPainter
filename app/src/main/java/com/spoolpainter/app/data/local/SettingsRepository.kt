package com.spoolpainter.app.data.local

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Settings>,
    externalScope: CoroutineScope,
) {
    val settings: StateFlow<Settings> = store.data.stateIn(
        scope = externalScope,
        started = SharingStarted.Eagerly,
        initialValue = Settings(),
    )

    suspend fun setUrl(url: String) {
        store.updateData { it.copy(url = url) }
    }

    suspend fun setSortOrder(order: SortOrder) {
        store.updateData { it.copy(sortOrder = order) }
    }

    suspend fun setThemeOverride(theme: ThemeOverride) {
        store.updateData { it.copy(themeOverride = theme) }
    }
}
