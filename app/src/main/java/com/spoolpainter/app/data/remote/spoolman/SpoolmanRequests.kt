package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament

data class CreateVendorRequest(
    val name: String,
    // SpoolmanDB import: the manufacturer name string when the vendor is
    // created from an external-DB filament pick; null omits the field.
    val external_id: String? = null,
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
    // SpoolmanDB import (server ≥ 0.26.0): external filament slug.
    val external_id: String? = null,
)

/** A filament after an override PATCH; [wrote] is false when nothing differed and no HTTP write was made. */
data class FilamentPatchResult(
    val filament: SpoolmanFilament,
    val wrote: Boolean,
)

data class PatchFilamentBody(
    val name: String? = null,
    val color_hex: String? = null,
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
 * Form fields routed from the UI to either [CreateFilamentRequest]
 * (new-filament path) or [PatchFilamentBody] (existing-filament path).
 * Null = "no override; use stored value or call-site default."
 *
 * `variant` is included so editing the Variant field on an existing spool
 * or existing filament cascades a patch to `extra.variant` (UI-13 partial,
 * v2.0). Empty/blank variant strings are normalised to null at the
 * applyOverridesIfNeeded boundary so they don't trigger pointless patches.
 */
data class ExpanderOverrides(
    val density: Float? = null,
    val diameter: Float? = null,
    val weight: Float? = null,
    val spoolWeight: Float? = null,
    val spoolWeightForSpool: Float? = null,
    val price: Float? = null,
    val spoolPrice: Float? = null,
    val variant: String? = null,
    /** v2.1 — filament-record edits unlocked on existing-spool path. Color +
     *  temps PATCH the filament alongside variant. sparseDiff in
     *  patchFilament collapses unchanged values to a no-op so passing these
     *  on every Save is cheap. */
    val colorHex: String? = null,
    val extruderTemp: Int? = null,
    val bedTemp: Int? = null,
) {
    companion object {
        val EMPTY = ExpanderOverrides()
    }
}

data class CreateSpoolRequest(
    val filament_id: Int,
    val price: Float? = null,
    val spool_weight: Float? = null,
    val extra: Map<String, String>? = null,
)

data class SpoolPatchBody(
    val extra: Map<String, String>? = null,
    // U32 B3: Spoolman applies PATCH fields in its SpoolParameters order,
    // initial_weight before remaining_weight, so one request can raise the
    // cap and set a remaining above the old one.
    val initial_weight: Float? = null,
    val remaining_weight: Float? = null,
    val price: Float? = null,
    val spool_weight: Float? = null,
)

data class ExtraFieldDef(
    val key: String? = null,
    val name: String,
    val field_type: String,
    val order: Int,
    val default_value: String,
)

/** Body of `POST /api/v1/spool/{id}/tag` (server ≥ 0.27.0). `uid` is
 *  1-128 chars, server-normalized to uppercase hex without separators;
 *  the app's CardUid.hex already matches that shape and is sent as-is. */
data class TagLinkBody(
    val uid: String,
    val format: String? = null,
)
