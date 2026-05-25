package com.spoolpainter.app.data.local

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val url: String = "",
    val sortOrder: SortOrder = SortOrder.Default,
    val themeOverride: ThemeOverride = ThemeOverride.System,
)

@Serializable
enum class SortOrder { Default, Alphabetical, MaterialThenColor }

@Serializable
enum class ThemeOverride { System, Light, Dark }
