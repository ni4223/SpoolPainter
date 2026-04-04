package com.spoolpainter.app.domain.models

import com.spoolpainter.app.data.local.MaterialDatabase
import org.json.JSONObject
import kotlin.math.log

data class OpenSpoolData(
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
    val lotNr: String? = null
) {
    fun toJson(): String {
        android.util.Log.d("OpenSpoolData", "toJson() called with: protocol=$protocol, version=$version, type=$type, colorHex=$colorHex, brand=$brand, minTemp=$minTemp, maxTemp=$maxTemp, bedMinTemp=$bedMinTemp, bedMaxTemp=$bedMaxTemp, subtype=$subtype, spoolId=$spoolId, lotNr=$lotNr")
        return JSONObject().apply {
            put("protocol", protocol)
            put("version", version)
            put("type", type)
            put("color_hex", colorHex ?: "")
            put("brand", brand)
            put("min_temp", minTemp)
            put("max_temp", maxTemp)
            bedMinTemp?.let { put("bed_min_temp", it) }
            bedMaxTemp?.let { put("bed_max_temp", it) }
            spoolId?.let { put("spool_id", it) }
            lotNr?.let { put("lot_nr", it) }
            if (subtype.isNotEmpty()) put("subtype", subtype)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): OpenSpoolData? {
            return try {
                // Remove language prefix if present (e.g., "en{...}" -> "{...}")
                val cleanJson = if (json.startsWith("{")) json else json.dropWhile { it != '{' }
                val jsonObj = JSONObject(cleanJson)
                if (jsonObj.optString("protocol") == "openspool") {
                    val type = jsonObj.optString("type", "Unknown")
                    val material = MaterialDatabase.getMaterial(type)
                    OpenSpoolData(
                        type = type,
                        colorHex = jsonObj.optString("color_hex", "").takeIf { it.isNotEmpty() },
                        brand = jsonObj.optString("brand", "Unknown"),
                        minTemp = jsonObj.optString("min_temp", material?.defaultMinTemp?.toString() ?: "200"),
                        maxTemp = jsonObj.optString("max_temp", material?.defaultMaxTemp?.toString() ?: "220"),
                        bedMinTemp = jsonObj.optString("bed_min_temp").takeIf { it.isNotEmpty() },
                        bedMaxTemp = jsonObj.optString("bed_max_temp").takeIf { it.isNotEmpty() },
                        subtype = jsonObj.optString("subtype", "Basic"),
                        spoolId = jsonObj.optString("spool_id").takeIf { it.isNotEmpty() },
                        lotNr = jsonObj.optString("lot_nr").takeIf { it.isNotEmpty() }
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }

        fun toOpenSpoolData(spool: FilamentSpool): OpenSpoolData {

            return OpenSpoolData(
                type = spool.material,
                colorHex = spool.colorHex,
                brand = spool.brand,
                minTemp = spool.minTemp.toString(),
                maxTemp = spool.maxTemp.toString(),
                subtype = spool.variant.ifEmpty { "Basic" },
                spoolId = spool.id?.toString()
            )
        }

        fun generateLotNr(): String = 
            java.util.UUID.randomUUID().toString().replace("-", "").take(9).uppercase()
    }

}