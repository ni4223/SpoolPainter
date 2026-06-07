package com.spoolpainter.app.hardware.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function tests for the Bambu binary tag parser. Everything here runs
 * without an Android runtime — `parseBambuTag` and `bambuDeriveKeys` operate
 * on plain byte arrays.
 *
 * Offsets reproduced inline (mirror BambuFormat's private constants). If
 * upstream changes the layout, these break — that's the point: the wire
 * format is the contract.
 */
class BambuFormatTest {

    @Test
    fun `parseBambuTag rejects buffers shorter than 1024 bytes`() {
        assertNull(parseBambuTag(ByteArray(0)))
        assertNull(parseBambuTag(ByteArray(1023)))
    }

    @Test
    fun `parseBambuTag returns null when filament type is blank`() {
        val data = ByteArray(1024)
        // FILAMENT_TYPE_POS (32) left as zero bytes → blank string
        assertNull(parseBambuTag(data))
    }

    @Test
    fun `parseBambuTag extracts type, colour, temps from documented offsets`() {
        val data = blankBambuBuffer()
        writeAscii(data, offset = 32, value = "PLA")
        writeAscii(data, offset = 64, value = "PLA Matte")
        // RGB at COLOR_RGBA_POS (80): R=0xFF G=0x88 B=0x00
        data[80] = 0xFF.toByte(); data[81] = 0x88.toByte(); data[82] = 0x00.toByte()
        // bed temp = 60, hotend max = 220, hotend min = 190 (uint16 LE)
        writeU16LE(data, offset = 102, value = 60)
        writeU16LE(data, offset = 104, value = 220)
        writeU16LE(data, offset = 106, value = 190)

        val payload = parseBambuTag(data)

        assertNotNull(payload)
        payload!!
        assertEquals("PLA", payload.type)
        assertEquals("FF8800", payload.colorHex)
        assertEquals("Bambu Lab", payload.brand)
        assertEquals("190", payload.minTemp)
        assertEquals("220", payload.maxTemp)
        assertEquals("60", payload.bedMinTemp)
        assertEquals("60", payload.bedMaxTemp)
        // detailedType "PLA Matte" starts with "PLA" → modifier "Matte"
        assertEquals("Matte", payload.subtype)
    }

    @Test
    fun `parseBambuTag falls back to Basic when detailed type is blank`() {
        val data = blankBambuBuffer()
        writeAscii(data, offset = 32, value = "PETG")
        // detailedType left as zero bytes → modifier blank → subtype = "Basic"
        val payload = parseBambuTag(data)
        assertNotNull(payload)
        assertEquals("Basic", payload!!.subtype)
    }

    @Test
    fun `parseBambuTag treats detailedType equal to filamentType as Basic`() {
        val data = blankBambuBuffer()
        writeAscii(data, offset = 32, value = "ABS")
        writeAscii(data, offset = 64, value = "ABS")
        val payload = parseBambuTag(data)
        assertNotNull(payload)
        assertEquals("Basic", payload!!.subtype)
    }

    @Test
    fun `bambuDeriveKeys returns 16 keys of 6 bytes each`() {
        val uid = byteArrayOf(0x04, 0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte(), 0xD4.toByte(), 0xE5.toByte(), 0xF6.toByte())
        // Salt is hex-encoded; HKDF requires it to be even-length ASCII hex.
        val salt = "DEADBEEFCAFEBABE0011223344556677"
        val keys = bambuDeriveKeys(uid, salt)
        assertEquals(16, keys.size)
        keys.forEach { assertEquals(6, it.size) }
    }

    @Test
    fun `bambuDeriveKeys is deterministic for fixed UID and salt`() {
        val uid = byteArrayOf(0x04, 0x12, 0x34, 0x56, 0x78, 0x9A.toByte(), 0xBC.toByte())
        val salt = "DEADBEEFCAFEBABE0011223344556677"
        val a = bambuDeriveKeys(uid, salt)
        val b = bambuDeriveKeys(uid, salt)
        a.zip(b).forEach { (x, y) -> assertTrue(x.contentEquals(y)) }
    }

    @Test
    fun `bambuDeriveKeys differs when UID changes`() {
        val salt = "DEADBEEFCAFEBABE0011223344556677"
        val uid1 = byteArrayOf(0x04, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
        val uid2 = byteArrayOf(0x04, 0x01, 0x02, 0x03, 0x04, 0x05, 0x07)
        val a = bambuDeriveKeys(uid1, salt)
        val b = bambuDeriveKeys(uid2, salt)
        // Sector 0 keys must diverge — the whole point of UID-bound HKDF.
        assertTrue(!a[0].contentEquals(b[0]))
    }

    private fun blankBambuBuffer(): ByteArray = ByteArray(1024)

    private fun writeAscii(buf: ByteArray, offset: Int, value: String) {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        bytes.copyInto(buf, offset)
        // Remaining bytes in the slot stay zero — extractString stops at first NUL.
    }

    private fun writeU16LE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
