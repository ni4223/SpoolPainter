package com.spoolpainter.app.data.local

import androidx.datastore.core.DataStore
import com.spoolpainter.app.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsRepository {
    val settings: StateFlow<Settings>
    suspend fun setUrl(url: String)
    suspend fun setSortOrder(order: SortOrder)
    suspend fun setThemeOverride(theme: ThemeOverride)
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val store: DataStore<Settings>,
    @AppScope externalScope: CoroutineScope,
) : SettingsRepository {

    override val settings: StateFlow<Settings> = store.data.stateIn(
        scope = externalScope,
        started = SharingStarted.Eagerly,
        initialValue = Settings(),
    )

    override suspend fun setUrl(url: String) {
        store.updateData { it.copy(url = url) }
    }

    override suspend fun setSortOrder(order: SortOrder) {
        store.updateData { it.copy(sortOrder = order) }
    }

    override suspend fun setThemeOverride(theme: ThemeOverride) {
        store.updateData { it.copy(themeOverride = theme) }
    }
}
