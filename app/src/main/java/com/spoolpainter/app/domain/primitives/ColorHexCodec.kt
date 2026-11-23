package com.spoolpainter.app.domain.primitives

/**
 * Colour hex helpers.
 *
 * U33 / UI-75: Spoolman's `color_hex` "supports alpha channel at the end"
 * (Spoolman v0.27 `api/v1/filament.py`), so a transparent colour is 8
 * characters, `RRGGBBAA`. The app shows and compares the RGB part only and
 * never drops a stored alpha unless the user changes the colour (D1, D2).
 * Tags always get 6-hex RGB (D3).
 */
object ColorHexCodec {
    private val HEX6 = Regex("^[0-9A-F]{6}$")
    private val HEX8 = Regex("^[0-9A-F]{8}$")

    /**
     * Strip `#`, keep the **first** 6 characters (the RGB of `RRGGBBAA`) and
     * uppercase. `"#ff0000"`, `"FF0000"` and `"ff000080"` all give `"FF0000"`.
     * Null for null or empty input.
     */
    fun canonicalise(raw: String?): String? =
        raw?.removePrefix("#")
            ?.let { if (it.length > 6) it.take(6) else it }
            ?.uppercase()
            ?.takeIf { it.isNotEmpty() }

    /**
     * The alpha of an 8-character `RRGGBBAA` colour (`"FF000080"` → `"80"`),
     * uppercased. Null for anything that isn't 8 hex digits.
     */
    fun alpha(raw: String?): String? =
        raw?.removePrefix("#")
            ?.uppercase()
            ?.takeIf { it.matches(HEX8) }
            ?.takeLast(2)

    /**
     * The `color_hex` to PATCH onto a filament when the form asks for
     * [requested] and Spoolman stores [stored]. Null means don't send it.
     *
     * - Same RGB → null, so an untouched transparent colour is never rewritten.
     * - Different RGB → the new RGB plus the stored alpha, if any (D2):
     *   `FF000080` with green requested → `00FF0080`.
     */
    fun overrideFor(requested: String?, stored: String?): String? {
        val rgb = canonicalise(requested)?.takeIf { it.matches(HEX6) } ?: return null
        if (rgb == canonicalise(stored)) return null
        return rgb + (alpha(stored) ?: "")
    }

    /**
     * Decode a color hex to its R/G/B channels (0..255). Canonicalises first
     * (strips `#`, takes the first 6 chars, uppercases), so `"#ff0000"`,
     * `"FF0000"` and `"ff000080"` all yield `(255, 0, 0)`. Returns null when the
     * input is null/blank or the canonical form is not exactly 6 hex digits.
     * Used by [SpoolMatchScorer] for RGB color-distance ranking (U20) and by
     * the picker swatches (U33).
     */
    fun toRgb(raw: String?): Triple<Int, Int, Int>? {
        val hex = canonicalise(raw) ?: return null
        if (!hex.matches(HEX6)) return null
        val value = hex.toInt(16)
        val r = (value shr 16) and 0xFF
        val g = (value shr 8) and 0xFF
        val b = value and 0xFF
        return Triple(r, g, b)
    }
}
