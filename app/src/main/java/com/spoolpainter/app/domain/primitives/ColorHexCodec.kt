package com.spoolpainter.app.domain.primitives

object ColorHexCodec {
    fun canonicalise(raw: String?): String? =
        raw?.removePrefix("#")
            ?.let { if (it.length > 6) it.takeLast(6) else it }
            ?.uppercase()
            ?.takeIf { it.isNotEmpty() }

    /**
     * Decode a color hex to its R/G/B channels (0..255). Canonicalises first
     * (strips `#`, takes the last 6 chars, uppercases), so `"#ff0000"`,
     * `"FF0000"` and `"ffff0000"` all yield `(255, 0, 0)`. Returns null when the
     * input is null/blank or the canonical form is not exactly 6 hex digits.
     * Used by [SpoolMatchScorer] for RGB color-distance ranking (U20).
     */
    fun toRgb(raw: String?): Triple<Int, Int, Int>? {
        val hex = canonicalise(raw) ?: return null
        if (hex.length != 6 || !hex.all { it in '0'..'9' || it in 'A'..'F' }) return null
        val value = hex.toInt(16)
        val r = (value shr 16) and 0xFF
        val g = (value shr 8) and 0xFF
        val b = value and 0xFF
        return Triple(r, g, b)
    }
}
