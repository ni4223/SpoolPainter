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
