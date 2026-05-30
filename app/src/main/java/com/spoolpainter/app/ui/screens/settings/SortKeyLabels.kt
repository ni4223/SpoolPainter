package com.spoolpainter.app.ui.screens.settings

import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SpoolSortKey

internal fun spoolSortKeyLabel(key: SpoolSortKey): String = when (key) {
    SpoolSortKey.Material -> "Material"
    SpoolSortKey.Brand -> "Brand"
    SpoolSortKey.Id -> "ID"
    SpoolSortKey.LastUsed -> "Last Used"
}

internal fun filamentSortKeyLabel(key: FilamentSortKey): String = when (key) {
    FilamentSortKey.Material -> "Material"
    FilamentSortKey.Brand -> "Brand"
    FilamentSortKey.Id -> "ID"
}
