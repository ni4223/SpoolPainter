package com.spoolpainter.app.hardware.nfc.vendor

/**
 * QIDI tag lookup tables — material code → (type, modifiers) and color code →
 * 24-bit RGB. Ported verbatim from
 * `OpenRFID/src/tag/qidi/constants.py` (GPL-3.0, see NOTICE for SHA).
 */
internal object QidiTables {

    data class Material(val type: String, val modifiers: List<String>)

    val MATERIALS: Map<Int, Material> = mapOf(
        0x01 to Material("PLA", emptyList()),
        0x02 to Material("PLA", listOf("Matte")),
        0x03 to Material("PLA", listOf("Metal")),
        0x04 to Material("PLA", listOf("Silk")),
        0x05 to Material("PLA-CF", emptyList()),
        0x06 to Material("PLA", listOf("Wood")),
        0x07 to Material("PLA", listOf("Basic")),
        0x08 to Material("PLA", listOf("Matte", "Basic")),
        0x0B to Material("ABS", emptyList()),
        0x0C to Material("ABS-GF", emptyList()),
        0x0D to Material("ABS", listOf("Metal")),
        0x0E to Material("ABS", listOf("Odorless")),
        0x12 to Material("ASA", emptyList()),
        0x13 to Material("ASA-AERO", emptyList()),
        0x18 to Material("PA", listOf("Ultra")),
        0x19 to Material("PA12-CF", emptyList()),
        0x1A to Material("PA-CF", listOf("Ultra", "CF25")),
        0x1E to Material("PAHT-CF", emptyList()),
        0x1F to Material("PAHT-GF", emptyList()),
        0x20 to Material("BVOH", listOf("For PAHT")),
        0x21 to Material("BVOH", listOf("For PET/PA")),
        0x22 to Material("PC-ABS", listOf("FR")),
        0x25 to Material("PET-CF", emptyList()),
        0x26 to Material("PET-GF", emptyList()),
        0x27 to Material("PETG", listOf("Basic")),
        0x28 to Material("PETG", listOf("Tough")),
        0x29 to Material("PETG", emptyList()),
        0x2C to Material("PPS-CF", emptyList()),
        0x2D to Material("PETG", listOf("Translucent")),
        0x2F to Material("PVA", emptyList()),
        0x31 to Material("TPU", listOf("AERO")),
        0x32 to Material("TPU", emptyList()),
    )

    val COLORS: Map<Int, Int> = mapOf(
        0x01 to 0xFAFAFA,
        0x02 to 0x060606,
        0x03 to 0xD9E3ED,
        0x04 to 0x5CF30F,
        0x05 to 0x63E492,
        0x06 to 0x2850FF,
        0x07 to 0xFE98FE,
        0x08 to 0xDFD628,
        0x09 to 0x228332,
        0x0A to 0x99DEFF,
        0x0B to 0x1714B0,
        0x0C to 0xCEC0FE,
        0x0D to 0xCADE4B,
        0x0E to 0x1353AB,
        0x0F to 0x5EA9FD,
        0x10 to 0xA878FF,
        0x11 to 0xFE717A,
        0x12 to 0xFF362D,
        0x13 to 0xE2DFCD,
        0x14 to 0x898F9B,
        0x15 to 0x6E3812,
        0x16 to 0xCAC59F,
        0x17 to 0xF28636,
        0x18 to 0xB87F2B,
    )
}
