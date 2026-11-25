package com.spoolpainter.app.domain.models

import com.spoolpainter.app.domain.primitives.ColorHexCodec

/**
 * Whether this filament is [material] (case-insensitive) in [colorHex]
 * (compared by [ColorHexCodec.canonicalise], so `#ff0000`, `FF0000` and
 * `FF000080` are one colour). The vendor- and variant-free part of the
 * filament match Save uses; Map tag uses it to check a picked spool is the
 * tag's product. Two missing colours count as equal, as they do for Save.
 */
fun SpoolmanFilament.isMaterialInColour(material: String, colorHex: String?): Boolean =
    (this.material ?: "").equals(material, ignoreCase = true) &&
        ColorHexCodec.canonicalise(color_hex) == ColorHexCodec.canonicalise(colorHex)
