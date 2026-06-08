package com.spoolpainter.app.hardware.nfc.vendor

import com.spoolpainter.app.hardware.nfc.parseBambuTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Adapter parity test (Q-U14b-5=A) — one happy path, asserts the registry
 * adapter returns the same payload as the underlying `parseBambuTag`. Heavy
 * coverage stays in `BambuFormatTest`.
 */
class BambuProcessorAdapterTest {

    @Test fun `BambuProcessor parse matches parseBambuTag for the same bytes`() {
        val data = ByteArray(1024)
        // Mirrors `BambuFormatTest.parseBambuTag extracts type, colour, temps`.
        // filament_type @ 32, detailed_type @ 64, color @ 80, bed/hotend at 102/104/106.
        "PLA".toByteArray(Charsets.US_ASCII).copyInto(data, destinationOffset = 32)
        "PLA Matte".toByteArray(Charsets.US_ASCII).copyInto(data, destinationOffset = 64)
        data[80] = 0xFF.toByte(); data[81] = 0x88.toByte(); data[82] = 0x00.toByte()
        // u16 LE
        data[102] = 60; data[103] = 0
        data[104] = 220.toByte(); data[105] = 0
        data[106] = 190.toByte(); data[107] = 0

        val direct = parseBambuTag(data)
        val viaAdapter = BambuProcessor.parse(
            uid = byteArrayOf(0x04, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66),
            raw = data,
            auth = null,
            settings = VendorSettings(),
        )
        assertNotNull(direct)
        assertEquals(direct, viaAdapter)
    }
}
