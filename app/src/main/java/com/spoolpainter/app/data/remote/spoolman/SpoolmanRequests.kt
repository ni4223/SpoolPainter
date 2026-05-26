package com.spoolpainter.app.data.remote.spoolman

data class CreateVendorRequest(
    val name: String,
)

data class CreateFilamentRequest(
    val name: String?,
    val vendor_id: Int,
    val material: String,
    val color_hex: String,
    val settings_extruder_temp: Int?,
    val settings_bed_temp: Int?,
    // Spoolman requires both, gt=0. Not surfaced in v1 UI; defaults are
    // material-derived (density) + universal consumer standard (diameter).
    val density: Float,
    val diameter: Float,
    // Net weight of a full spool in grams. Defaulted at the call site so a
    // fresh pairing yields a sensible "remaining weight" without UI input.
    val weight: Float,
    val extra: Map<String, String>? = null,
)

data class CreateSpoolRequest(
    val filament_id: Int,
    val extra: Map<String, String>? = null,
)

data class SpoolPatchBody(
    val extra: Map<String, String>? = null,
)

data class ExtraFieldDef(
    val key: String? = null,
    val name: String,
    val field_type: String,
    val order: Int,
    val default_value: String,
)
