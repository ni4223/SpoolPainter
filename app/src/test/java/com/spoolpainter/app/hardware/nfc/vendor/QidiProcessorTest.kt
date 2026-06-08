package com.spoolpainter.app.hardware.nfc.vendor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * QIDI tag parser unit tests. Builds 1024-byte tag fixtures with a
 * QIDI-shaped sector 1 (offset 64..112) and feeds them to
 * [QidiProcessor.parse]. Auth + chip-type filtering are covered by the
 * dispatcher tests; this file is purely about byte-layout parsing and
 * lookup-table coverage.
 */
class QidiProcessorTest {

    private fun fixture(material: Int, color: Int, mfr: Int = 0xA1, padNonZero: Boolean = false): ByteArray {
        val raw = ByteArray(1024)
        raw[64] = material.toByte()
        raw[65] = color.toByte()
        raw[66] = mfr.toByte()
        if (padNonZero) raw[67] = 0xAB.toByte()
        return raw
    }

    private val uid = byteArrayOf(0x04, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
    private val settings = VendorSettings()

    @Test fun `PLA basic parses with brand QIDI`() {
        val raw = fixture(material = 0x01, color = 0x01)
        val out = QidiProcessor.parse(uid, raw, auth = null, settings = settings)
        assertNotNull(out)
        assertEquals("PLA", out!!.type)
        assertEquals("QIDI", out.brand)
        assertEquals("FAFAFA", out.colorHex)
        assertEquals("Basic", out.subtype)
    }

    @Test fun `PETG modifier maps to subtype`() {
        val raw = fixture(material = 0x27, color = 0x06)
        val out = QidiProcessor.parse(uid, raw, auth = null, settings = settings)
        assertNotNull(out)
        assertEquals("PETG", out!!.type)
        assertEquals("Basic", out.subtype)
        assertEquals("2850FF", out.colorHex)
    }

    @Test fun `ABS no modifier defaults subtype to Basic`() {
        val raw = fixture(material = 0x0B, color = 0x12)
        val out = QidiProcessor.parse(uid, raw, auth = null, settings = settings)!!
        assertEquals("ABS", out.type)
        assertEquals("Basic", out.subtype)
    }

    @Test fun `ASA family parses cleanly`() {
        val raw = fixture(material = 0x12, color = 0x18)
        val out = QidiProcessor.parse(uid, raw, auth = null, settings = settings)!!
        assertEquals("ASA", out.type)
    }

    @Test fun `PA-CF preserves first modifier as subtype`() {
        val raw = fixture(material = 0x1A, color = 0x14)
        val out = QidiProcessor.parse(uid, raw, auth = null, settings = settings)!!
        assertEquals("PA-CF", out.type)
        assertEquals("Ultra", out.subtype)
    }

    @Test fun `TPU plain parses with Basic subtype`() {
        val raw = fixture(material = 0x32, color = 0x09)
        val out = QidiProcessor.parse(uid, raw, auth = null, settings = settings)!!
        assertEquals("TPU", out.type)
        assertEquals("Basic", out.subtype)
    }

    @Test fun `unknown material code rejected`() {
        // 0xFE is not in QidiTables.MATERIALS.
        val raw = fixture(material = 0xFE, color = 0x01)
        assertNull(QidiProcessor.parse(uid, raw, auth = null, settings = settings))
    }

    @Test fun `unknown color code rejected`() {
        val raw = fixture(material = 0x01, color = 0xFE)
        assertNull(QidiProcessor.parse(uid, raw, auth = null, settings = settings))
    }

    @Test fun `non-zero trailing byte rejects parse`() {
        val raw = fixture(material = 0x01, color = 0x01, padNonZero = true)
        assertNull(QidiProcessor.parse(uid, raw, auth = null, settings = settings))
    }

    @Test fun `zero material code rejected`() {
        val raw = fixture(material = 0x00, color = 0x01)
        assertNull(QidiProcessor.parse(uid, raw, auth = null, settings = settings))
    }

    @Test fun `short raw bytes rejected`() {
        val raw = ByteArray(50)
        assertNull(QidiProcessor.parse(uid, raw, auth = null, settings = settings))
    }
}
