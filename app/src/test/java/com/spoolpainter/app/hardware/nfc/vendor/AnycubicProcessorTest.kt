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
        sku: String = "",
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
        // SKU at 0x14..0x24, brand at 0x28..0x38, filament at 0x3C..0x4C — ASCII null-padded.
        sku.toByteArray(Charsets.US_ASCII).copyInto(raw, 0x14)
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

    @Test fun `SKU is read from 0x14`() {
        val out = AnycubicProcessor.parse(uid, fixture(sku = "AHPLBK-101"), auth = null, settings = settings)!!
        assertEquals("AHPLBK-101", out.sku)
    }

    @Test fun `blank SKU reads as null`() {
        val out = AnycubicProcessor.parse(uid, fixture(sku = ""), auth = null, settings = settings)!!
        assertNull(out.sku)
    }

    @Test fun `SKU filling its whole 16-byte field is read without running into the next field`() {
        val out = AnycubicProcessor.parse(uid, fixture(sku = "ABCDEFGHIJKLMNOP"), auth = null, settings = settings)!!
        assertEquals("ABCDEFGHIJKLMNOP", out.sku)
    }

    /** Pages 0x00..0x23 of a real Anycubic High Speed PLA tag, dumped with
     *  NFC Tools. Unlike the fixture's `AHPLRR-107`, its SKU has no leading
     *  "A" — the field is taken verbatim, not normalised. */
    @Test fun `real tag dump - SKU without leading A`() {
        val pages = listOf(
            "53E385BD", "2C930001", "BE480000", "E1101200", "7B006500", "48504C31",
            "382D3130", "36000000", "00000000", "00000000", "41430000", "00000000",
            "00000000", "00000000", "00000000", "504C4100", "00000000", "00000000",
            "00000000", "00000000", "FFBD9C00", "00000000", "00000000", "3200C800",
            "BE00E600", "00000000", "00000000", "00000000", "00000000", "37004100",
            "AF004A01", "E8030000", "00000000", "00000000", "00000000", "00000000",
        )
        val raw = pages.joinToString("").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        val out = AnycubicProcessor.parse(uid, raw, auth = null, settings = settings)!!

        assertEquals("HPL18-106", out.sku)
    }
}
