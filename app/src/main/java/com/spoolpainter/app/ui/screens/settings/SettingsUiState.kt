package com.spoolpainter.app.ui.screens.settings

import com.spoolpainter.app.data.local.SortOrder
import com.spoolpainter.app.data.local.ThemeOverride

// connectivity field added in U9 (depends on U3 ConnectivityState).
data class SettingsUiState(
    val url: String = "",
    val sortOrder: SortOrder = SortOrder.Default,
    val themeOverride: ThemeOverride = ThemeOverride.System,
)
