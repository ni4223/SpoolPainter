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
    // Spoolman requires density / diameter / weight (gt=0). Defaults computed
    // at call site from material map + per-form expander overrides.
    val density: Float,
    val diameter: Float,
    val weight: Float,
    // Optional expander overrides (Q-U8-8=A: null fields omitted by Gson).
    val spool_weight: Float? = null,
    val price: Float? = null,
    val extra: Map<String, String>? = null,
)

data class PatchFilamentBody(
    val name: String? = null,
    val settings_extruder_temp: Int? = null,
    val settings_bed_temp: Int? = null,
    val density: Float? = null,
    val diameter: Float? = null,
    val weight: Float? = null,
    val spool_weight: Float? = null,
    val price: Float? = null,
    val extra: Map<String, String>? = null,
)

/**
 * Five expander fields routed from the form to either
 * [CreateFilamentRequest] (new-filament path) or [PatchFilamentBody]
 * (existing-filament path). Null = "no override; use stored value or
 * call-site default."
 */
data class ExpanderOverrides(
    val density: Float? = null,
    val diameter: Float? = null,
    val weight: Float? = null,
    val spoolWeight: Float? = null,
    val price: Float? = null,
) {
    companion object {
        val EMPTY = ExpanderOverrides()
    }
}

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
