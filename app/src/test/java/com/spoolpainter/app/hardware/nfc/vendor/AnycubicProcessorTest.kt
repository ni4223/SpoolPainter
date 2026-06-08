package com.spoolpainter.app.hardware.nfc.vendor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Anycubic Ultralight tag parser tests. Builds 144-byte raw fixtures with
 * the four-byte magic + ASCII fields + ARGB color block, then asserts the
 * parser extracts them correctly.
 */
class AnycubicProcessorTest {

    private val uid = byteArrayOf(0x04, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
    private val settings = VendorSettings()

    private fun fixture(
        brand: String = "Anycubic",
        filamentType: String = "PLA",
        a: Int = 0xFF, b: Int = 0xCC, g: Int = 0x80, r: Int = 0x10,
        extruderMin: Int = 200, extruderMax: Int = 220, bedMax: Int = 60,
        magicCorrect: Boolean = true,
    ): ByteArray {
        val raw = ByteArray(144)
        if (magicCorrect) {
            raw[0x10] = 0x7B; raw[0x11] = 0x00; raw[0x12] = 0x65; raw[0x13] = 0x00
        } else {
            raw[0x10] = 0xAA.toByte(); raw[0x11] = 0xBB.toByte()
        }
        // Brand at 0x28..0x38, filament at 0x3C..0x4C — ASCII null-padded.
        brand.toByteArray(Charsets.US_ASCII).copyInto(raw, 0x28)
        filamentType.toByteArray(Charsets.US_ASCII).copyInto(raw, 0x3C)
        // Color (a,b,g,r byte order at 0x50..0x53).
        raw[0x50] = a.toByte(); raw[0x51] = b.toByte(); raw[0x52] = g.toByte(); raw[0x53] = r.toByte()
        // Temps (LE u16).
        raw[0x60] = (extruderMin and 0xFF).toByte(); raw[0x61] = ((extruderMin shr 8) and 0xFF).toByte()
        raw[0x62] = (extruderMax and 0xFF).toByte(); raw[0x63] = ((extruderMax shr 8) and 0xFF).toByte()
        raw[0x76] = (bedMax and 0xFF).toByte(); raw[0x77] = ((bedMax shr 8) and 0xFF).toByte()
        return raw
    }

    @Test fun `PLA happy path`() {
        val raw = fixture()
        val out = AnycubicProcessor.parse(uid, raw, auth = null, settings = settings)
        assertNotNull(out)
        assertEquals("PLA", out!!.type)
        assertEquals("Anycubic", out.brand)
        assertEquals("10" + "80" + "CC", out.colorHex)
        assertEquals("200", out.minTemp)
        assertEquals("220", out.maxTemp)
        assertEquals("60", out.bedMaxTemp)
    }

    @Test fun `header magic mismatch rejects parse`() {
        val raw = fixture(magicCorrect = false)
        assertNull(AnycubicProcessor.parse(uid, raw, auth = null, settings = settings))
    }

    @Test fun `PLA+ split into type PLA and modifier +`() {
        val raw = fixture(filamentType = "PLA+")
        val out = AnycubicProcessor.parse(uid, raw, auth = null, settings = settings)!!
        assertEquals("PLA", out.type)
        // Subtype is the first modifier — "+".
        assertEquals("+", out.subtype)
    }

    @Test fun `PLA-Matte hyphen splits to type PLA and modifier Matte`() {
        val raw = fixture(filamentType = "PLA-Matte")
        val out = AnycubicProcessor.parse(uid, raw, auth = null, settings = settings)!!
        assertEquals("PLA", out.type)
        assertEquals("Matte", out.subtype)
    }

    @Test fun `blank brand falls back to Anycubic`() {
        val raw = fixture(brand = "")
        val out = AnycubicProcessor.parse(uid, raw, auth = null, settings = settings)!!
        assertEquals("Anycubic", out.brand)
    }

    @Test fun `short raw bytes rejected`() {
        assertNull(AnycubicProcessor.parse(uid, ByteArray(50), auth = null, settings = settings))
    }
}
