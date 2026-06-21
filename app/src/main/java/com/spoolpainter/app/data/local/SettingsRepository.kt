package com.spoolpainter.app.data.local

import androidx.datastore.core.DataStore
import com.spoolpainter.app.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsRepository {
    val settings: StateFlow<Settings>

    /**
     * Suspends until the first real persisted value is read from disk.
     *
     * [settings] is a `stateIn` flow with an eager `Settings()` default, so its
     * `.value` returns defaults until DataStore's async load completes. Callers
     * that must observe the persisted value before acting (e.g. the one-time
     * "What's new" decision at cold start) need this instead of `settings.value`.
     */
    suspend fun awaitSettings(): Settings
    suspend fun setUrl(url: String)
    suspend fun setSpoolSortKey(key: SpoolSortKey)
    suspend fun setSpoolSortDirection(direction: SortDirection)
    suspend fun setFilamentSortKey(key: FilamentSortKey)
    suspend fun setFilamentSortDirection(direction: SortDirection)
    suspend fun setThemeOverride(theme: ThemeOverride)
    suspend fun setCurrency(currency: Currency)
    suspend fun setBambuSalt(salt: String)
    suspend fun setCrealitySalt(salt: String)
    suspend fun setCrealityEncKey(key: String)
    suspend fun setLastSeenWhatsNewVersion(version: Int)
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

    // Reads the raw DataStore flow, which genuinely suspends until the on-disk
    // value is loaded — unlike settings.value, which returns the eager default.
    override suspend fun awaitSettings(): Settings = store.data.first()

    override suspend fun setUrl(url: String) {
        store.updateData { it.copy(url = url) }
    }

    override suspend fun setSpoolSortKey(key: SpoolSortKey) {
        store.updateData { it.copy(spoolSortKey = key) }
    }

    override suspend fun setSpoolSortDirection(direction: SortDirection) {
        store.updateData { it.copy(spoolSortDirection = direction) }
    }

    override suspend fun setFilamentSortKey(key: FilamentSortKey) {
        store.updateData { it.copy(filamentSortKey = key) }
    }

    override suspend fun setFilamentSortDirection(direction: SortDirection) {
        store.updateData { it.copy(filamentSortDirection = direction) }
    }

    override suspend fun setThemeOverride(theme: ThemeOverride) {
        store.updateData { it.copy(themeOverride = theme) }
    }

    override suspend fun setCurrency(currency: Currency) {
        store.updateData { it.copy(currency = currency) }
    }

    override suspend fun setBambuSalt(salt: String) {
        store.updateData { it.copy(bambuSalt = salt) }
    }

    override suspend fun setCrealitySalt(salt: String) {
        store.updateData { it.copy(crealitySalt = salt) }
    }

    override suspend fun setCrealityEncKey(key: String) {
        store.updateData { it.copy(crealityEncKey = key) }
    }

    override suspend fun setLastSeenWhatsNewVersion(version: Int) {
        store.updateData { it.copy(lastSeenWhatsNewVersion = version) }
    }
}
