package com.spoolpainter.app.domain.models

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class SpoolmanSpool(
    val id: Int? = null,
    val filament: SpoolmanFilament,
    val remaining_weight: Float? = null,
    // Net filament weight the spool started with. Spoolman 0.27 stores a
    // remaining_weight PATCH as used = max(initial_weight - remaining, 0), so
    // remaining can never exceed it (database/spool.py update()).
    val initial_weight: Float? = null,
    val used_weight: Float = 0f,
    val price: Float? = null,
    // Spoolman supports spool_weight on both spool + filament records;
    // the spool-level value overrides the filament default. Server-side
    // create-time fallback is `spool.spool_weight = filament.spool_weight`
    // when the request doesn't supply one (database/spool.py:56-58).
    val spool_weight: Float? = null,
    val location: String? = null,
    val lot_nr: String? = null,
    val archived: Boolean = false,
    val extra: Map<String, String>? = null,
    // ISO-8601 string from Spoolman; null on freshly-created spools that
    // haven't had filament consumed yet. Used by SortKey.LastUsed.
    val last_used: String? = null,
    // Native tags (server ≥ 0.27.0): the UIDs linked to this spool in
    // GET /spool payloads. Absent on older servers; readers treat a
    // missing list as empty rather than trusting the reflective binding.
    val tags: List<SpoolmanTag> = emptyList(),
)

data class SpoolmanFilament(
    val id: Int,
    val name: String? = null,
    val material: String? = null,
    val vendor: SpoolmanVendor? = null,
    val color_hex: String? = null,
    val settings_extruder_temp: Int? = null,
    val settings_bed_temp: Int? = null,
    val extra: Map<String, String>? = null,
    // U8: stored filament metadata (density/diameter/weight/spool_weight/price).
    val density: Float? = null,
    val diameter: Float? = null,
    val weight: Float? = null,
    val spool_weight: Float? = null,
    val price: Float? = null,
    // SpoolmanDB import (server ≥ 0.26.0): the external filament slug this
    // record was created from. Used to de-duplicate repeated imports.
    val external_id: String? = null,
)

data class SpoolmanVendor(
    val id: Int? = null,
    val name: String,
    // U32: the vendor's empty-spool (tare) weight, the last level Spoolman's
    // own measure falls back to (Spoolman 0.27 database/spool.py measure).
    val empty_spool_weight: Float? = null,
    // SpoolmanDB import: for DB-imported vendors this is the manufacturer
    // name string from the external database.
    val external_id: String? = null,
)

data class SpoolmanResponse<T>(
    val items: List<T>
)

data class SpoolmanInfo(
    val version: String? = null,
    // Spoolman ≥ 0.26.0: display name of the configured external filament
    // database (e.g. "SpoolmanDB"); null on older servers / when unset.
    val external_db_name: String? = null,
)

/** A native NFC tag as returned by `POST /api/v1/spool/{id}/tag`
 *  (server ≥ 0.27.0). The server omits null fields; `added` is an
 *  ISO-8601 datetime string. */
data class SpoolmanTag(
    val uid: String,
    val format: String? = null,
    val added: String? = null,
)

/** Body of the 409 the tag endpoints return when the UID is already linked
 *  to another spool or filament: the holder ids identify the conflict. */
data class TagConflictBody(
    val message: String,
    val spool_id: Int? = null,
    val filament_id: Int? = null,
)

/** Outcome of one automatic legacy→native tag mirror run: plain counts,
 *  no timestamps — each completed run overwrites the previous state. */
sealed interface TagSyncState {
    data object Idle : TagSyncState
    data class Finished(
        val mirrored: Int,
        val conflicts: List<TagSyncConflict>,
        val failed: Int,
    ) : TagSyncState
}

/** One UID the mirror could not link natively because the server holds
 *  it elsewhere (409). The mirror never steals tags, so the holder keeps
 *  the UID; `holderSpoolId` is null when the conflict body names no spool
 *  (e.g. a filament holds the UID). */
data class TagSyncConflict(
    val uid: String,
    val targetSpoolId: Int,
    val holderSpoolId: Int? = null,
)

private val GSON = Gson()

/** Null-safe parser for the 409 conflict body; null on malformed JSON or a
 *  body without the contractually required `message`. */
fun parseTagConflict(json: String): TagConflictBody? = runCatching {
    GSON.fromJson(json, TagConflictBody::class.java)
}.getOrNull()?.takeIf { body ->
    // Gson bypasses the Kotlin constructor, so a payload missing
    // "message" leaves the non-null typed field null at runtime.
    @Suppress("SENSELESS_COMPARISON")
    body.message != null
}

/**
 * Native tag API (`POST /api/v1/spool/{id}/tag` etc.) shipped in Spoolman
 * 0.27.0. Tolerant gate, unlike the strict external-DB-search triple check:
 * pre-release suffixes like "0.27.0-beta" or "0.27.0rc1" compare by their
 * numeric prefix, so a 0.27.0 pre-release counts as supported. A null
 * receiver, null/blank version, or no leading dotted-integer prefix at all
 * fails closed → false.
 */
fun SpoolmanInfo?.supportsNativeTags(): Boolean {
    if (this == null) return false
    val numbers = parseLeadingDottedInts(version) ?: return false
    return isAtLeastVersion(numbers, major = 0, minor = 27, patch = 0)
}

/** "0.27.1" → [0, 27, 1]; "0.27.0-rc1" → [0, 27, 0]; "1" → [1]. Stops at
 *  the first non-numeric segment; null when no numeric prefix exists. */
internal fun parseLeadingDottedInts(version: String?): List<Int>? {
    val trimmed = version?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val numbers = mutableListOf<Int>()
    for (segment in trimmed.split('.')) {
        val digits = segment.takeWhile { it.isDigit() }
        if (digits.isEmpty()) break
        // Out-of-int-range segments (absurdly long digit runs) stop the
        // parse → caller fails closed rather than throwing.
        numbers += digits.toIntOrNull() ?: break
        if (digits.length < segment.length) break
    }
    return numbers.takeIf { it.isNotEmpty() }
}

/** Compares the first three components (missing ones count as 0) against a
 *  minimum version. */
private fun isAtLeastVersion(parsed: List<Int>, major: Int, minor: Int, patch: Int): Boolean {
    val actual = List(3) { parsed.getOrNull(it) ?: 0 }
    return when {
        actual[0] != major -> actual[0] > major
        actual[1] != minor -> actual[1] > minor
        else -> actual[2] >= patch
    }
}

/**
 * One entry of Spoolman's external filament database ("SpoolmanDB"),
 * `GET /api/v1/external/filament/search` (server ≥ 0.26.0). Every field
 * except id/manufacturer/name/material may be null or absent (the server
 * serialises with response_model_exclude_none). `color_hex` and
 * `color_hexes` are mutually exclusive; hexes carry no '#' prefix.
 *
 * Wire format is snake_case; pinned contract names are camelCase, hence
 * the explicit [SerializedName] mapping.
 */
data class ExternalFilament(
    val id: String,
    val manufacturer: String,
    val name: String,
    val material: String,
    val density: Double? = null,
    val weight: Double? = null,
    @SerializedName("spool_weight") val spoolWeight: Double? = null,
    @SerializedName("spool_type") val spoolType: String? = null,
    val diameter: Double? = null,
    @SerializedName("color_hex") val colorHex: String? = null,
    @SerializedName("color_hexes") val colorHexes: List<String>? = null,
    @SerializedName("extruder_temp") val extruderTemp: Int? = null,
    @SerializedName("bed_temp") val bedTemp: Int? = null,
    val finish: String? = null,
    val pattern: String? = null,
    val translucent: Boolean? = null,
    val glow: Boolean? = null,
    @SerializedName("multi_color_direction") val multiColorDirection: String? = null,
)

/** One page of [ExternalFilament] search results. [totalCount] is the total
 *  match count from the `x-total-count` response header (falls back to the
 *  page size when the header is absent). */
data class ExternalFilamentPage(
    val items: List<ExternalFilament>,
    val totalCount: Int,
)
