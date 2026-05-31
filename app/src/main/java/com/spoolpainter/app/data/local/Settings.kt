package com.spoolpainter.app.data.local

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val url: String = "",
    val spoolSortKey: SpoolSortKey = SpoolSortKey.Id,
    val spoolSortDirection: SortDirection = SortDirection.Desc,
    val filamentSortKey: FilamentSortKey = FilamentSortKey.Id,
    val filamentSortDirection: SortDirection = SortDirection.Desc,
    val themeOverride: ThemeOverride = ThemeOverride.Dark,
    val currency: Currency = Currency.Dollar,
)

@Serializable
enum class SpoolSortKey { Material, Brand, Id, LastUsed }

@Serializable
enum class FilamentSortKey { Material, Brand, Id }

@Serializable
enum class SortDirection { Asc, Desc }

@Serializable
enum class ThemeOverride { Light, Dark }

@Serializable
enum class Currency(val symbol: String) {
    Dollar("$"),
    Euro("€"),
    Generic("¤"),
}
