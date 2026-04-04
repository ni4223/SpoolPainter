package com.spoolpainter.app.domain.models

import com.spoolpainter.app.data.local.MaterialDatabase
import com.spoolpainter.app.domain.models.OpenSpoolData

data class FilamentSpool(
    val id: Int? = null,
    val material: String = "Unknown",
    val variant: String = "",
    val brand: String = "Unknown",
    val colorHex: String?,
    val minTemp: Int?,
    val maxTemp: Int?,
    val bedMinTemp: Int?,
    val bedMaxTemp: Int?,
    val remainingWeight: Float? = null,
    val usedWeight: Float = 0f,
    val location: String? = null,
    val lotNr: String? = null,
    val archived: Boolean = false,
    val spoolmanName: String?
) {
    val displayName: String
        get() = if (variant.isNotEmpty()) "$material $variant" else material
    
    companion object {
        fun fromSpoolman(spool: SpoolmanSpool): FilamentSpool {
            val material = spool.filament.material ?: "Unknown"
            val materialData = MaterialDatabase.getMaterial(material)
            val extruderTemp = spool.filament.settings_extruder_temp
            val bedTemp = spool.filament.settings_bed_temp
            
            val minTemp: Int?
            val maxTemp: Int?
            if (materialData != null && extruderTemp != null && 
                extruderTemp >= materialData.defaultMinTemp && extruderTemp <= materialData.defaultMaxTemp) {
                minTemp = materialData.defaultMinTemp
                maxTemp = materialData.defaultMaxTemp
            } else {
                minTemp = extruderTemp
                maxTemp = extruderTemp?.plus(20)
            }
            
            val bedMinTemp: Int?
            val bedMaxTemp: Int?
            if (materialData != null && bedTemp != null && 
                bedTemp >= materialData.defaultBedMinTemp && bedTemp <= materialData.defaultBedMaxTemp) {
                bedMinTemp = materialData.defaultBedMinTemp
                bedMaxTemp = materialData.defaultBedMaxTemp
            } else {
                bedMinTemp = bedTemp
                bedMaxTemp = bedTemp?.plus(10)
            }
            
            return FilamentSpool(
                id = spool.id,
                material = material,
                brand = spool.filament.vendor?.name ?: "Unknown",
                colorHex = spool.filament.color_hex
                    ?.removePrefix("#")
                    ?.let { if (it.length > 6) it.takeLast(6) else it }
                    ?.uppercase()
                    ?.takeIf { it.isNotEmpty() },
                minTemp = minTemp,
                maxTemp = maxTemp,
                bedMinTemp = bedMinTemp,
                bedMaxTemp = bedMaxTemp,
                remainingWeight = spool.remaining_weight,
                usedWeight = spool.used_weight,
                location = spool.location,
                lotNr = spool.lot_nr,
                archived = spool.archived,
                spoolmanName = spool.filament.name ?: ""
            )
        }

        fun fromOpenSpool(spool: OpenSpoolData) : FilamentSpool{
            val material = MaterialDatabase.getMaterial(spool.type)
            return FilamentSpool(
                id = spool.spoolId?.toIntOrNull(),
                material = spool.type,
                variant = if (spool.subtype != "Basic") spool.subtype else "",
                brand = spool.brand,
                colorHex = spool.colorHex,
                minTemp = spool.minTemp.toIntOrNull() ?: material?.defaultMinTemp,
                maxTemp = spool.maxTemp.toIntOrNull() ?: material?.defaultMaxTemp,
                bedMinTemp = spool.bedMinTemp?.toIntOrNull() ?: material?.defaultBedMinTemp,
                bedMaxTemp = spool.bedMaxTemp?.toIntOrNull() ?: material?.defaultBedMaxTemp,
                spoolmanName = ""
            )
        }
    }
}
