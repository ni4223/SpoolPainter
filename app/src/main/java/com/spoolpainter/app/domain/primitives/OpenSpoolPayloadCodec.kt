package com.spoolpainter.app.domain.primitives

import com.spoolpainter.app.domain.models.OpenSpoolPayload
import org.json.JSONException
import org.json.JSONObject

object OpenSpoolPayloadCodec {

    fun fromJson(json: String): OpenSpoolDecodeResult {
        // v1 quirk preserved: tolerate language prefix or other leading chars before '{'.
        val cleanJson = if (json.startsWith("{")) json else json.dropWhile { it != '{' }
        if (cleanJson.isEmpty()) return OpenSpoolDecodeResult.NotOpenSpool

        val obj = try {
            JSONObject(cleanJson)
        } catch (_: JSONException) {
            return OpenSpoolDecodeResult.NotOpenSpool
        }

        if (obj.optString("protocol", "") != "openspool") {
            return OpenSpoolDecodeResult.NotOpenSpool
        }

        val type = obj.optString("type", "")
        if (type.isEmpty()) return OpenSpoolDecodeResult.Malformed("missing type")
        val brand = obj.optString("brand", "")
        if (brand.isEmpty()) return OpenSpoolDecodeResult.Malformed("missing brand")
        val minTemp = obj.optString("min_temp", "")
        if (minTemp.isEmpty()) return OpenSpoolDecodeResult.Malformed("missing min_temp")
        val maxTemp = obj.optString("max_temp", "")
        if (maxTemp.isEmpty()) return OpenSpoolDecodeResult.Malformed("missing max_temp")

        val payload = OpenSpoolPayload(
            protocol = "openspool",
            version = obj.optString("version", "1.0"),
            type = type,
            colorHex = obj.optString("color_hex", "").takeIf { it.isNotEmpty() },
            brand = brand,
            minTemp = minTemp,
            maxTemp = maxTemp,
            bedMinTemp = obj.optString("bed_min_temp", "").takeIf { it.isNotEmpty() },
            bedMaxTemp = obj.optString("bed_max_temp", "").takeIf { it.isNotEmpty() },
            subtype = obj.optString("subtype", "Basic"),
            spoolId = obj.optString("spool_id", "").takeIf { it.isNotEmpty() },
            lotNr = obj.optString("lot_nr", "").takeIf { it.isNotEmpty() },
        )
        return OpenSpoolDecodeResult.Success(payload)
    }

    fun toJson(payload: OpenSpoolPayload): String {
        val obj = JSONObject()
        obj.put("protocol", payload.protocol)
        obj.put("version", payload.version)
        obj.put("type", payload.type)
        // v1 quirk preserved: color_hex emitted as empty string when null.
        obj.put("color_hex", payload.colorHex ?: "")
        obj.put("brand", payload.brand)
        obj.put("min_temp", payload.minTemp)
        obj.put("max_temp", payload.maxTemp)
        payload.bedMinTemp?.let { obj.put("bed_min_temp", it) }
        payload.bedMaxTemp?.let { obj.put("bed_max_temp", it) }
        payload.spoolId?.let { obj.put("spool_id", it) }
        // lot_nr is intentionally never emitted — see OpenSpoolPayload.lotNr KDoc.
        if (payload.subtype.isNotEmpty()) obj.put("subtype", payload.subtype)
        return obj.toString()
    }
}
