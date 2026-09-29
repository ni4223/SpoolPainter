package com.spoolpainter.app.domain.models

data class OpenSpoolPayload(
    val protocol: String = "openspool",
    val version: String = "1.0",
    val type: String,
    val colorHex: String?,
    val brand: String,
    val minTemp: String,
    val maxTemp: String,
    val bedMinTemp: String? = null,
    val bedMaxTemp: String? = null,
    val subtype: String = "Basic",
    val spoolId: String? = null,
    // Read-side only — codec never emits this field on encode (FR-14.1).
    val lotNr: String? = null,
    // Read-side only, like lotNr: the vendor's product code decoded from a
    // vendor tag (Anycubic SKU, Bambu material variant ID, Snapmaker SKU).
    // Saved to the Spoolman filament's article_number; never encoded.
    val sku: String? = null,
)
