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
    val bambuSalt: String = "",
    val crealitySalt: String = "",
    val crealityEncKey: String = "",
    // Highest app versionCode whose "What's new" showcase the user has seen.
    // 0 = never recorded; drives the one-time showcase trigger in
    // WhatsNewController. Unknown to pre-U15 payloads, read back as 0.
    val lastSeenWhatsNewVersion: Int = 0,
    // Where NFC tag UIDs are persisted: BOTH writes the legacy card_uids
    // extra field AND the native tag API (server ≥ 0.27.0); NATIVE_ONLY /
    // LEGACY_ONLY restrict to one. Unknown to pre-<this-release> payloads,
    // read back as BOTH.
    val tagStorageMode: TagStorageMode = TagStorageMode.BOTH,
)

@Serializable
enum class SpoolSortKey { Material, Brand, Id, LastUsed }

@Serializable
enum class FilamentSortKey { Material, Brand, Id }

@Serializable
enum class SortDirection { Asc, Desc }

@Serializable
enum class TagStorageMode { BOTH, NATIVE_ONLY, LEGACY_ONLY }

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
