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
enum class Currency(val symbol: String, val displayName: String) {
    Dollar("$", "US Dollar"),
    Euro("€", "Euro"),
    Pound("£", "British Pound"),
    Yen("¥", "Japanese Yen"),
    Yuan("¥", "Chinese Yuan"),
    Rupee("₹", "Indian Rupee"),
    Won("₩", "South Korean Won"),
    Franc("₣", "Swiss Franc"),
    CanadianDollar("C$", "Canadian Dollar"),
    AustralianDollar("A$", "Australian Dollar"),
    NewZealandDollar("NZ$", "New Zealand Dollar"),
    Real("R$", "Brazilian Real"),
    Peso("$", "Mexican Peso"),
    Krona("kr", "Swedish Krona"),
    Lira("₺", "Turkish Lira"),
    Ruble("₽", "Russian Ruble"),
    Rand("R", "South African Rand"),
    Shekel("₪", "Israeli Shekel"),
    Dirham("د.إ", "UAE Dirham"),
    HongKongDollar("HK$", "Hong Kong Dollar"),
    SingaporeDollar("S$", "Singapore Dollar"),
    Generic("¤", "Money"),
}
