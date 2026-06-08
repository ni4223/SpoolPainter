package com.spoolpainter.app.hardware.nfc.vendor

/**
 * Creality material code → temps + modifiers. Ported from
 * `OpenRFID/src/tag/creality/constants.py` (GPL-3.0, see NOTICE for SHA).
 */
internal object CrealityTables {

    data class Data(
        val type: String,
        val modifiers: List<String>,
        val hotendMin: Int,
        val hotendMax: Int,
        val bed: Int,
        val drying: Int,
        val dryingHours: Int,
    )

    val FILAMENT_CODE_TO_DATA: Map<String, Data> = mapOf(
        "01001" to Data("PLA", listOf("Hyper"), 190, 240, 50, 50, 8),
        "02001" to Data("PLA-CF", listOf("Hyper"), 190, 240, 50, 50, 8),
        "06002" to Data("PETG", listOf("Hyper"), 220, 270, 70, 60, 8),
        "03001" to Data("ABS", listOf("Hyper"), 240, 280, 80, 80, 8),
        "09002" to Data("PLA", listOf("Ender", "Fast"), 190, 240, 50, 50, 6),
        "04001" to Data("PLA", listOf("CR"), 190, 240, 50, 50, 8),
        "05001" to Data("PLA", listOf("CR", "Silk"), 190, 240, 50, 50, 8),
        "06001" to Data("PETG", listOf("CR"), 220, 270, 70, 60, 8),
        "07001" to Data("ABS", listOf("CR"), 240, 280, 100, 80, 8),
        "08001" to Data("PLA", listOf("Ender"), 190, 240, 50, 50, 8),
        "09001" to Data("PLA", listOf("EN", "PLA+"), 190, 240, 50, 50, 8),
        "10001" to Data("TPU", listOf("HP"), 190, 240, 40, 65, 8),
        "11001" to Data("PA", listOf("CR", "Nylon"), 250, 270, 50, 80, 8),
        "13001" to Data("PLA", listOf("CR", "Carbon"), 190, 240, 50, 50, 8),
        "14001" to Data("PLA", listOf("CR", "Matte"), 190, 240, 50, 50, 8),
        "15001" to Data("PLA", listOf("CR", "Fluo"), 190, 240, 50, 50, 8),
        "16001" to Data("TPU", listOf("CR"), 210, 240, 40, 65, 8),
        "17001" to Data("PLA", listOf("CR", "Wood"), 190, 240, 50, 50, 8),
        "18001" to Data("PLA", listOf("HP", "Ultra"), 190, 240, 50, 50, 8),
        "19001" to Data("ASA", listOf("HP"), 240, 280, 90, 80, 8),
        "12003" to Data("PA-CF", listOf("Hyper", "PAHT"), 280, 320, 90, 80, 10),
        "12002" to Data("PA-CF", listOf("Hyper", "PPA"), 280, 320, 100, 100, 8),
        "07002" to Data("PC", listOf("Hyper"), 250, 270, 110, 80, 8),
        "01601" to Data("PLA", listOf("Soleyin", "Ultra"), 190, 240, 50, 50, 8),
    )
}
