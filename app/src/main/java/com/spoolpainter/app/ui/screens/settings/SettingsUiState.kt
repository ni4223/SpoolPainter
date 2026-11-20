package com.spoolpainter.app.ui.screens.settings

import com.spoolpainter.app.data.local.Currency
import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.data.local.SpoolSortKey
import com.spoolpainter.app.data.local.TagStorageMode
import com.spoolpainter.app.domain.models.TagSyncState

data class SettingsUiState(
    val url: String = "",
    val spoolSortKey: SpoolSortKey = SpoolSortKey.Id,
    val spoolSortDirection: SortDirection = SortDirection.Desc,
    val filamentSortKey: FilamentSortKey = FilamentSortKey.Id,
    val filamentSortDirection: SortDirection = SortDirection.Desc,
    val currency: Currency = Currency.Dollar,
    val bambuSalt: String = "",
    val crealitySalt: String = "",
    val crealityEncKey: String = "",
    // Issue #19 Phase 3 — NFC tag storage preference, plus the Spoolman
    // server capability info that contextualises it. serverVersion /
    // supportsNativeTags are sourced from the repository's serverInfo
    // StateFlow; they stay null/false until a test-connection/refresh has
    // probed the server.
    val tagStorageMode: TagStorageMode = TagStorageMode.BOTH,
    val serverVersion: String? = null,
    val supportsNativeTags: Boolean = false,
    // Issue #19 Phase 4 — latest automatic legacy→native mirror run; Idle
    // until a run has completed against a native-tag-capable server.
    val tagSyncState: TagSyncState = TagSyncState.Idle,
)
