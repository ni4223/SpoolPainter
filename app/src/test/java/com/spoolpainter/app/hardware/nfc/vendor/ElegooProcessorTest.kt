package com.spoolpainter.app.hardware.nfc.vendor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Elegoo Ultralight tag parser tests. Builds 144-byte raw fixtures with the
 * 41-byte filament_data block at offset 0x40..0x69 (EE marker + material id
 * + RGBA color + temps + diameter/weight).
 */
class ElegooProcessorTest {

    private val uid = byteArrayOf(0x04, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
    private val settings = VendorSettings()

    private fun fixture(
        materialFamily: Int = 0x00, materialModifier: Int = 0x00,
        r: Int = 0xAB, g: Int = 0xCD, b: Int = 0xEF, a: Int = 0xFF,
        minTemp: Int = 200, maxTemp: Int = 220,
        markerByte: Int = 0xEE,
    ): ByteArray {
        val raw = ByteArray(144)
        // Filament block starts at 0x40.
        val base = 0x40
        // EE marker at filament[0x01..0x05] = raw[0x41..0x45].
        for (i in 0x01..0x04) raw[base + i] = markerByte.toByte()
        // Material at 0x0C/0x0D big-endian (high byte family, low byte modifier).
        raw[base + 0x0C] = materialFamily.toByte()
        raw[base + 0x0D] = materialModifier.toByte()
        // Color RGBA at 0x10..0x13.
        raw[base + 0x10] = r.toByte()
        raw[base + 0x11] = g.toByte()
        raw[base + 0x12] = b.toByte()
        raw[base + 0x13] = a.toByte()
        // Temps big-endian u16.
        raw[base + 0x14] = ((minTemp shr 8) and 0xFF).toByte()
        raw[base + 0x15] = (minTemp and 0xFF).toByte()
        raw[base + 0x16] = ((maxTemp shr 8) and 0xFF).toByte()
        raw[base + 0x17] = (maxTemp and 0xFF).toByte()
        return raw
    }

    @Test fun `PLA basic happy path`() {
        val raw = fixture(materialFamily = 0x00, materialModifier = 0x00)
        val out = ElegooProcessor.parse(uid, raw, auth = null, settings = settings)
        assertNotNull(out)
        assertEquals("PLA", out!!.type)
        assertEquals("Elegoo", out.brand)
        assertEquals("ABCDEF", out.colorHex)
        assertEquals("200", out.minTemp)
        assertEquals("220", out.maxTemp)
        assertEquals("Basic", out.subtype)
    }

    @Test fun `PLA+ subtype`() {
        val raw = fixture(materialFamily = 0x00, materialModifier = 0x01)
        val out = ElegooProcessor.parse(uid, raw, auth = null, settings = settings)!!
        assertEquals("PLA", out.type)
        assertEquals("+", out.subtype)
    }

    @Test fun `PA6 collapses 6 modifier into type`() {
        val raw = fixture(materialFamily = 0x04, materialModifier = 0x04)
        val out = ElegooProcessor.parse(uid, raw, auth = null, settings = settings)!!
        // Upstream collapses modifier "6" into the material name.
        assertEquals("PA6", out.type)
        assertEquals("Basic", out.subtype)
    }

    @Test fun `PA12-CF collapses 12 and keeps CF as subtype`() {
        val raw = fixture(materialFamily = 0x04, materialModifier = 0x07)
        val out = ElegooProcessor.parse(uid, raw, auth = null, settings = settings)!!
        assertEquals("PA12", out.type)
        assertEquals("CF", out.subtype)
    }

    @Test fun `EE marker mismatch rejects parse`() {
        val raw = fixture(markerByte = 0xAA)
        assertNull(ElegooProcessor.parse(uid, raw, auth = null, settings = settings))
    }

    @Test fun `unknown material subtype rejected`() {
        // Family 0x00 modifier 0xFE not in tables.
        val raw = fixture(materialFamily = 0x00, materialModifier = 0xFE)
        assertNull(ElegooProcessor.parse(uid, raw, auth = null, settings = settings))
    }

    @Test fun `short raw bytes rejected`() {
        assertNull(ElegooProcessor.parse(uid, ByteArray(50), auth = null, settings = settings))
    }
}
