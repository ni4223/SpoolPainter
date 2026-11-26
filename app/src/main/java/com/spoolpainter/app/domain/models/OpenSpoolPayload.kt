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
    // U28: read-side only, never encoded. Parser notes for "Share last NFC
    // scan" (e.g. an unknown vendor type code), and one user-facing line to
    // show after the read.
    val decodeNotes: List<String> = emptyList(),
    val readNotice: String? = null,
    // Read-side only, like lotNr: the vendor's product code decoded from a
    // vendor tag (Anycubic SKU, Bambu material variant ID, Snapmaker SKU).
    // Saved to the Spoolman filament's article_number; never encoded.
    val sku: String? = null,
)

/**
 * [OpenSpoolPayload.sku] if it reads as a real product code. Erased or garbled
 * tag bytes decode to U+FFFD or control characters (US_ASCII decoding), and
 * those must never reach Spoolman: the fill only writes empty fields, so a
 * junk code would block the right one forever.
 */
fun OpenSpoolPayload.productCode(): String? =
    sku?.trim()?.takeIf { code -> code.isNotEmpty() && code.all { it in ' '..'~' } }
