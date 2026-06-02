package com.spoolpainter.app.hardware.nfc

import android.util.Log
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val BTAG = "BambuFormat"

// ── Offsets (mirrored from bambu_parser.dart) ─────────────────────────────────
private const val MATERIAL_VARIANT_ID_POS = 1 * 16 + 0
private const val MATERIAL_VARIANT_ID_LEN = 8
private const val MATERIAL_ID_POS = 1 * 16 + 8
private const val MATERIAL_ID_LEN = 8

private const val FILAMENT_TYPE_POS = 2 * 16 + 0
private const val FILAMENT_TYPE_LEN = 16

private const val DETAILED_FILAMENT_TYPE_POS = 4 * 16 + 0
private const val DETAILED_FILAMENT_TYPE_LEN = 16

private const val COLOR_RGBA_POS = 5 * 16 + 0
private const val SPOOL_WEIGHT_POS = 5 * 16 + 4
private const val FILAMENT_DIAMETER_POS = 5 * 16 + 8

private const val DRYING_TEMP_POS = 6 * 16 + 0
private const val DRYING_TIME_POS = 6 * 16 + 2
private const val BED_TEMP_TYPE_POS = 6 * 16 + 4
private const val BED_TEMP_POS = 6 * 16 + 6
private const val HOTEND_MAX_TEMP_POS = 6 * 16 + 8
private const val HOTEND_MIN_TEMP_POS = 6 * 16 + 10

private const val NOZZLE_DIAMETER_POS = 8 * 16 + 12
private const val TRAY_UID_POS = 9 * 16 + 0
private const val TRAY_UID_LEN = 16

private const val SPOOL_WIDTH_POS = 10 * 16 + 4
private const val PRODUCTION_DATETIME_POS = 12 * 16 + 0
private const val PRODUCTION_DATETIME_LEN = 16
private const val FILAMENT_LENGTH_POS = 14 * 16 + 4

private const val FORMAT_IDENTIFIER_POS = 16 * 16 + 0
private const val COLOR_COUNT_POS = 16 * 16 + 2
private const val SECOND_COLOR_POS = 16 * 16 + 4

private const val FORMAT_COLOR_INFO = 0x0002
private const val TAG_TOTAL_SIZE = 1024

// ── Bambu HKDF key derivation ────────────────────────────────────────────────
private fun hmacSha256Bambu(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data)
}

private fun bambuHexToBytes(hex: String): ByteArray =
    ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

fun bambuDeriveKeys(uid: ByteArray, saltHex: String): List<ByteArray> {
    Log.d(BTAG, "bambuDeriveKeys: uid=${uid.joinToString("") { "%02X".format(it) }} saltHex=${saltHex.take(16)}…(len=${saltHex.length})")
    val saltBytes = bambuHexToBytes(saltHex)
    val info = byteArrayOf(*"RFID-A".toByteArray(Charsets.UTF_8), 0x00)
    val prk = hmacSha256Bambu(saltBytes, uid)
    Log.d(BTAG, "bambuDeriveKeys: PRK=${prk.joinToString("") { "%02X".format(it) }}")
    val output = mutableListOf<Byte>()
    var t = byteArrayOf()
    var counter = 1
    while (output.size < 96) {
        t = hmacSha256Bambu(prk, t + info + counter.toByte())
        output.addAll(t.toList())
        counter++
    }
    val okm = output.take(96).toByteArray()
    val keys = List(16) { i -> okm.copyOfRange(i * 6, i * 6 + 6) }
    Log.d(BTAG, "bambuDeriveKeys: sector0 key=${keys[0].joinToString("") { "%02X".format(it) }}")
    return keys
}

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun extractString(data: ByteArray, pos: Int, len: Int): String {
    val slice = data.copyOfRange(pos, pos + len)
    val end = slice.indexOfFirst { it == 0.toByte() }
    return String(if (end >= 0) slice.copyOfRange(0, end) else slice, Charsets.US_ASCII)
}

private fun extractUint16LE(data: ByteArray, pos: Int): Int =
    (data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)

private fun extractUint32LE(data: ByteArray, pos: Int): Int =
    (data[pos].toInt() and 0xFF) or
    ((data[pos + 1].toInt() and 0xFF) shl 8) or
    ((data[pos + 2].toInt() and 0xFF) shl 16) or
    ((data[pos + 3].toInt() and 0xFF) shl 24)

private fun extractFloat32LE(data: ByteArray, pos: Int): Float =
    ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).float

// ── Parser ────────────────────────────────────────────────────────────────────
fun parseBambuTag(data: ByteArray): OpenSpoolPayload? {
    Log.d(BTAG, "parseBambuTag: dataSize=${data.size}")
    if (data.size < TAG_TOTAL_SIZE) {
        Log.w(BTAG, "parseBambuTag: data too short (${data.size} < $TAG_TOTAL_SIZE)")
        return null
    }

    val filamentType = extractString(data, FILAMENT_TYPE_POS, FILAMENT_TYPE_LEN)
    Log.d(BTAG, "parseBambuTag: filamentType='$filamentType'")
    if (filamentType.isBlank()) {
        Log.w(BTAG, "parseBambuTag: filamentType is blank — parse failed")
        return null
    }

    val detailedType = extractString(data, DETAILED_FILAMENT_TYPE_POS, DETAILED_FILAMENT_TYPE_LEN)

    val r = data[COLOR_RGBA_POS].toInt() and 0xFF
    val g = data[COLOR_RGBA_POS + 1].toInt() and 0xFF
    val b = data[COLOR_RGBA_POS + 2].toInt() and 0xFF
    val colorHexStr = "%02X%02X%02X".format(r, g, b)

    val bedTemp = extractUint16LE(data, BED_TEMP_POS)
    val hotendMaxTemp = extractUint16LE(data, HOTEND_MAX_TEMP_POS)
    val hotendMinTemp = extractUint16LE(data, HOTEND_MIN_TEMP_POS)

    val modifier = if (detailedType.startsWith(filamentType))
        detailedType.substring(filamentType.length).trim()
    else detailedType

    val finalSubtype = if (modifier.isNotBlank() && modifier != filamentType) modifier else "Basic"

    return OpenSpoolPayload(
        protocol = "openspool",
        version = "1.0",
        type = filamentType,
        colorHex = colorHexStr,
        brand = "Bambu Lab",
        minTemp = hotendMinTemp.toString(),
        maxTemp = hotendMaxTemp.toString(),
        bedMinTemp = bedTemp.toString(),
        bedMaxTemp = bedTemp.toString(),
        subtype = finalSubtype,
        spoolId = null
    )
}
