package com.spoolpainter.app.hardware.nfc.vendor

import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import android.util.Log
import com.spoolpainter.app.domain.models.OpenSpoolPayload

private const val TAG = "ElegooProcessor"

/**
 * Elegoo MifareUltralight tag processor. The chip stores a 41-byte
 * `filament_data` block at offset 0x40..0x69. A four-byte EE marker at
 * offset 0x01..0x05 identifies the format. Material is a big-endian uint16
 * lookup at 0x0C of filament_data. Color is RGBA (r,g,b,a byte order) at
 * 0x10..0x13. Numeric values are big-endian.
 *
 * Ported from `OpenRFID/src/tag/elegoo/processor.py` (GPL-3.0, see NOTICE).
 */
object ElegooProcessor : VendorTagProcessor {
    override val id = VendorId.Elegoo
    override val displayName = "Elegoo"

    override fun isEnabled(settings: VendorSettings) = true

    override fun matchesChipType(techList: List<String>): Boolean =
        // Same NfcA fallback as AnycubicProcessor — see comment there.
        // EE marker check in parse() guards against false positives.
        techList.contains(MifareUltralight::class.java.name) ||
            techList.contains(NfcA::class.java.name)

    override fun deriveAuthKeys(uid: ByteArray, settings: VendorSettings): VendorAuth? = null

    override fun parse(
        uid: ByteArray,
        raw: ByteArray,
        auth: VendorAuth?,
        settings: VendorSettings,
    ): OpenSpoolPayload? {
        if (raw.size < 0x69) {
            Log.d(TAG, "parse: raw too short (${raw.size})")
            return null
        }
        val filament = raw.copyOfRange(0x40, 0x69)

        // EE EE EE EE marker at offsets 0x01..0x05 (i.e. four bytes 0x01..0x04 inclusive).
        for (i in 0x01..0x04) {
            if (filament[i] != 0xEE.toByte()) {
                Log.d(TAG, "parse: EE marker mismatch at filament[$i] = ${filament[i].toInt() and 0xFF}")
                return null
            }
        }

        val materialFamily = filament[0x0C].toInt() and 0xFF
        val materialModifier = filament[0x0D].toInt() and 0xFF
        val material = ElegooTables.MATERIALS[materialFamily to materialModifier] ?: run {
            Log.w(TAG, "parse: unknown material subtype %02X%02X".format(materialFamily, materialModifier))
            return null
        }

        // Color RGBA — bytes are r, g, b, a in that order at 0x10..0x13.
        val r = filament[0x10].toInt() and 0xFF
        val g = filament[0x11].toInt() and 0xFF
        val b = filament[0x12].toInt() and 0xFF
        val colorHex = "%02X%02X%02X".format(r, g, b)

        val minTemp = readU16BE(filament, 0x14)
        val maxTemp = readU16BE(filament, 0x16)

        val subtype = material.modifiers.firstOrNull() ?: "Basic"

        return OpenSpoolPayload(
            type = material.type,
            colorHex = colorHex,
            brand = "Elegoo",
            minTemp = minTemp.toString(),
            maxTemp = maxTemp.toString(),
            // Upstream leaves bed temp as 0 (unimplemented marker). Preserve
            // that — the form prefill treats "0" the same as null.
            bedMinTemp = "0",
            bedMaxTemp = "0",
            subtype = subtype,
            spoolId = null,
        )
    }

    private fun readU16BE(data: ByteArray, pos: Int): Int =
        ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
}
