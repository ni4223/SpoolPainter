package com.spoolpainter.app.ui.screens.settings

import com.spoolpainter.app.data.local.Currency
import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.data.local.SpoolSortKey

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
)
