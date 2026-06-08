package com.spoolpainter.app.hardware.nfc.vendor

import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import android.util.Log
import com.spoolpainter.app.domain.models.OpenSpoolPayload

private const val TAG = "AnycubicProcessor"

/**
 * Anycubic MifareUltralight tag processor. No auth — page reads return raw
 * bytes directly. The tag is identified by the four-byte magic
 * `7B 00 65 00` at offset 0x10. ARGB color is stored in a/b/g/r byte order
 * at 0x50..0x53 (note: not r/g/b/a — quirk of the Anycubic format upstream).
 *
 * Ported from `OpenRFID/src/tag/anycubic/processor.py` (GPL-3.0, see NOTICE).
 */
object AnycubicProcessor : VendorTagProcessor {
    override val id = VendorId.Anycubic
    override val displayName = "Anycubic"

    // No keys needed; always enabled.
    override fun isEnabled(settings: VendorSettings) = true

    override fun matchesChipType(techList: List<String>): Boolean =
        // Some Android stacks (moto g stylus 2025) don't surface
        // MifareUltralight in the techList for genuine Ultralight chips —
        // only NfcA. The reader falls back to raw NfcA READ commands in
        // that case, so we accept either here. False positives are caught
        // by the parser's header magic check (0x10..0x14 == 7B 00 65 00).
        techList.contains(MifareUltralight::class.java.name) ||
            techList.contains(NfcA::class.java.name)

    // Ultralight tags have no auth.
    override fun deriveAuthKeys(uid: ByteArray, settings: VendorSettings): VendorAuth? = null

    override fun parse(
        uid: ByteArray,
        raw: ByteArray,
        auth: VendorAuth?,
        settings: VendorSettings,
    ): OpenSpoolPayload? {
        if (raw.size < 0x80) {
            Log.d(TAG, "parse: raw too short (${raw.size})")
            return null
        }
        // Header magic check: bytes 0x10..0x14 must be 7B 00 65 00.
        if (raw[0x10] != 0x7B.toByte() ||
            raw[0x11] != 0x00.toByte() ||
            raw[0x12] != 0x65.toByte() ||
            raw[0x13] != 0x00.toByte()
        ) {
            Log.d(TAG, "parse: header magic mismatch")
            return null
        }

        val rawBrand = readAscii(raw, 0x28, 16)
        // Anycubic writes a short brand code ("AC") in this slot rather
        // than the full vendor name. Normalize so the form prefill matches
        // what users expect.
        val brand = when (rawBrand.uppercase()) {
            "AC", "ANYCUBIC", "" -> "Anycubic"
            else -> rawBrand
        }
        val filamentType = readAscii(raw, 0x3C, 16)
        if (filamentType.isBlank()) return null

        // Split type on space or '-'; "PLA+" -> type="PLA", modifier list ["+"].
        val rawTypes = filamentType.replace("-", " ").split(" ").filter { it.isNotBlank() }.toMutableList()
        if (rawTypes.isEmpty()) return null
        if (rawTypes[0].endsWith("+")) {
            rawTypes[0] = rawTypes[0].dropLast(1)
            rawTypes.add("+")
        }
        val type = rawTypes[0]
        val modifiers = rawTypes.drop(1)

        // Color order is a/b/g/r per upstream — the byte at 0x50 is alpha,
        // 0x51 is blue, 0x52 is green, 0x53 is red.
        val a = raw[0x50].toInt() and 0xFF
        val b = raw[0x51].toInt() and 0xFF
        val g = raw[0x52].toInt() and 0xFF
        val r = raw[0x53].toInt() and 0xFF
        val colorHex = "%02X%02X%02X".format(r, g, b)

        val extruderMin = readU16LE(raw, 0x60)
        val extruderMax = readU16LE(raw, 0x62)
        val bedMax = readU16LE(raw, 0x76)

        val subtype = modifiers.firstOrNull() ?: "Basic"

        return OpenSpoolPayload(
            type = type,
            colorHex = colorHex,
            brand = brand,
            minTemp = extruderMin.toString(),
            maxTemp = extruderMax.toString(),
            bedMinTemp = bedMax.toString(),
            bedMaxTemp = bedMax.toString(),
            subtype = subtype,
            spoolId = null,
        )
    }

    private fun readAscii(data: ByteArray, pos: Int, len: Int): String {
        val end = (pos + len).coerceAtMost(data.size)
        val slice = data.copyOfRange(pos, end)
        val nullIdx = slice.indexOfFirst { it == 0.toByte() }
        return String(if (nullIdx >= 0) slice.copyOfRange(0, nullIdx) else slice, Charsets.US_ASCII)
    }

    private fun readU16LE(data: ByteArray, pos: Int): Int =
        (data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)
}
