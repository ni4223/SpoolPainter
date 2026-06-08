package com.spoolpainter.app.hardware.nfc.vendor

import android.nfc.tech.MifareClassic
import android.util.Log
import com.spoolpainter.app.domain.models.OpenSpoolPayload

private const val TAG = "QidiProcessor"

/**
 * QIDI MifareClassic 1k tag processor. Auth uses the chip's default key
 * (`0xFF * 6` for every sector) — no per-tag derivation. The tag layout
 * stores three single-byte codes at offset 64 (sector 1) plus 45 bytes of
 * mandatory zeros; the codes index into [QidiTables].
 *
 * Ported from `OpenRFID/src/tag/qidi/processor.py` (GPL-3.0, see NOTICE).
 */
object QidiProcessor : VendorTagProcessor {
    override val id = VendorId.Qidi
    override val displayName = "QIDI"

    // QIDI tags use default keys, so they're always readable without any
    // user-supplied secret. Always enabled.
    override fun isEnabled(settings: VendorSettings) = true

    override fun matchesChipType(techList: List<String>): Boolean =
        techList.contains(MifareClassic::class.java.name)

    override fun deriveAuthKeys(uid: ByteArray, settings: VendorSettings): VendorAuth {
        val defaultKey = ByteArray(6) { 0xFF.toByte() }
        val keys = List(16) { defaultKey }
        return VendorAuth(keysA = keys, keysB = null)
    }

    override fun parse(
        uid: ByteArray,
        raw: ByteArray,
        auth: VendorAuth?,
        settings: VendorSettings,
    ): OpenSpoolPayload? {
        if (raw.size < 112) {
            Log.d(TAG, "parse: raw too short (${raw.size})")
            return null
        }
        val sectorOne = raw.copyOfRange(64, 112)
        val materialCode = sectorOne[0].toInt() and 0xFF
        val colorCode = sectorOne[1].toInt() and 0xFF
        val manufacturerCode = sectorOne[2].toInt() and 0xFF
        // Bytes 3..47 of sector 1 must be all zero — the QIDI format leaves
        // them reserved. Any non-zero byte means the chip belongs to another
        // vendor and we MUST reject it (otherwise random MifareClassic data
        // would coincidentally match three random byte slots).
        for (i in 3 until 48) {
            if (sectorOne[i] != 0.toByte()) {
                Log.d(TAG, "parse: trailing byte $i is non-zero (${sectorOne[i].toInt() and 0xFF})")
                return null
            }
        }
        if (materialCode == 0 || colorCode == 0 || manufacturerCode == 0) {
            Log.d(TAG, "parse: zero code (mat=$materialCode color=$colorCode mfr=$manufacturerCode)")
            return null
        }
        val material = QidiTables.MATERIALS[materialCode] ?: run {
            Log.w(TAG, "parse: unknown material code $materialCode")
            return null
        }
        val colorRgb = QidiTables.COLORS[colorCode] ?: run {
            Log.w(TAG, "parse: unknown color code $colorCode")
            return null
        }
        val colorHex = "%06X".format(colorRgb and 0xFFFFFF)
        val subtype = material.modifiers.firstOrNull() ?: "Basic"

        return OpenSpoolPayload(
            type = material.type,
            colorHex = colorHex,
            brand = "QIDI",
            // Upstream stores 0 for temps; the form prefill handles "0"
            // gracefully (treated like null).
            minTemp = "0",
            maxTemp = "0",
            bedMinTemp = "0",
            bedMaxTemp = "0",
            subtype = subtype,
            spoolId = null,
        )
    }
}
