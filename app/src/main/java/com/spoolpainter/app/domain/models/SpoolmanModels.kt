package com.spoolpainter.app.domain.models

data class SpoolmanSpool(
    val id: Int? = null,
    val filament: SpoolmanFilament,
    val remaining_weight: Float? = null,
    val used_weight: Float = 0f,
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
)

data class SpoolmanVendor(
    val id: Int? = null,
    val name: String,
)

data class SpoolmanResponse<T>(
    val items: List<T>
)

data class SpoolmanInfo(
    val version: String? = null,
)
