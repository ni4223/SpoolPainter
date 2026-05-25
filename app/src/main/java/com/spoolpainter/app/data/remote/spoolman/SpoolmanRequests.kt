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
)

data class CreateSpoolRequest(
    val filament_id: Int,
    val lot_nr: String,
)

data class UpdateSpoolLotNrRequest(
    val lot_nr: String,
)
