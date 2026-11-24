package com.spoolpainter.app.domain.models

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
