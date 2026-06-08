package com.spoolpainter.app.hardware.nfc.vendor

/**
 * Elegoo material/modifier subtype lookup. Two-byte material id (high byte =
 * material family, low byte = modifier id). Ported verbatim from
 * `OpenRFID/src/tag/elegoo/constants.py` (GPL-3.0, see NOTICE for SHA).
 *
 * Upstream merges special modifiers `6` and `12` into the material name
 * (PA + 6 → PA6, PA + 12 → PA12) and removes them from the modifier list;
 * we do the same below to match upstream output.
 */
internal object ElegooTables {

    data class Material(val type: String, val modifiers: List<String>)

    private fun bake(type: String, modifiers: List<String>): Material {
        var t = type
        val m = modifiers.toMutableList()
        if (m.remove("6")) t += "6"
        if (m.remove("12")) t += "12"
        return Material(t, m)
    }

    val MATERIALS: Map<Pair<Int, Int>, Material> = buildMap {
        // 0x00 PLA
        put(0x00 to 0x00, bake("PLA", emptyList()))
        put(0x00 to 0x01, bake("PLA", listOf("+")))
        put(0x00 to 0x02, bake("PLA", listOf("Pro")))
        put(0x00 to 0x03, bake("PLA", listOf("Silk")))
        put(0x00 to 0x04, bake("PLA", listOf("CF")))
        put(0x00 to 0x05, bake("PLA", listOf("Carbon")))
        put(0x00 to 0x06, bake("PLA", listOf("Matte")))
        put(0x00 to 0x07, bake("PLA", listOf("Fluo")))
        put(0x00 to 0x08, bake("PLA", listOf("Wood")))
        put(0x00 to 0x09, bake("PLA", listOf("Basic")))
        put(0x00 to 0x0A, bake("PLA", listOf("RAPID", "+")))
        put(0x00 to 0x0B, bake("PLA", listOf("Marble")))
        put(0x00 to 0x0C, bake("PLA", listOf("Galaxy")))
        put(0x00 to 0x0D, bake("PLA", listOf("Red", "Copper")))
        put(0x00 to 0x0E, bake("PLA", listOf("Sparkle")))
        // 0x01 PETG
        put(0x01 to 0x00, bake("PETG", emptyList()))
        put(0x01 to 0x01, bake("PETG", listOf("CF")))
        put(0x01 to 0x02, bake("PETG", listOf("GF")))
        put(0x01 to 0x03, bake("PETG", listOf("Pro")))
        put(0x01 to 0x04, bake("PETG", listOf("Translucent")))
        put(0x01 to 0x05, bake("PETG", listOf("RAPID")))
        // 0x02 ABS
        put(0x02 to 0x00, bake("ABS", emptyList()))
        put(0x02 to 0x01, bake("ABS", listOf("GF")))
        // 0x03 TPU
        put(0x03 to 0x00, bake("TPU", emptyList()))
        put(0x03 to 0x01, bake("TPU", listOf("95A")))
        put(0x03 to 0x02, bake("TPU", listOf("RAPID", "95A")))
        // 0x04 PA (incl. PA6/PA12 via "6"/"12" modifier collapse)
        put(0x04 to 0x00, bake("PA", emptyList()))
        put(0x04 to 0x01, bake("PA", listOf("CF")))
        put(0x04 to 0x03, bake("PA", listOf("HT", "CF")))
        put(0x04 to 0x04, bake("PA", listOf("6")))
        put(0x04 to 0x05, bake("PA", listOf("6", "CF")))
        put(0x04 to 0x06, bake("PA", listOf("12")))
        put(0x04 to 0x07, bake("PA", listOf("12", "CF")))
        // 0x05..0x0E single-modifier families
        put(0x05 to 0x00, bake("CPE", emptyList()))
        put(0x06 to 0x00, bake("PC", emptyList()))
        put(0x06 to 0x01, bake("PC", listOf("TG")))
        put(0x06 to 0x02, bake("PC", listOf("FR")))
        put(0x07 to 0x00, bake("PVA", emptyList()))
        put(0x08 to 0x00, bake("ASA", emptyList()))
        put(0x09 to 0x00, bake("BVOH", emptyList()))
        put(0x0A to 0x00, bake("EVA", emptyList()))
        put(0x0B to 0x00, bake("HIPS", emptyList()))
        put(0x0C to 0x00, bake("PP", emptyList()))
        put(0x0C to 0x01, bake("PP", listOf("CF")))
        put(0x0C to 0x02, bake("PP", listOf("GF")))
        put(0x0D to 0x00, bake("PPA", emptyList()))
        put(0x0D to 0x01, bake("PPA", listOf("CF")))
        put(0x0D to 0x02, bake("PPA", listOf("GF")))
        put(0x0E to 0x00, bake("PPS", emptyList()))
        put(0x0E to 0x02, bake("PPS", listOf("CF")))
    }
}
