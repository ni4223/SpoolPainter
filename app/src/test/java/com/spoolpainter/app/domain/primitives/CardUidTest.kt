package com.spoolpainter.app.domain.primitives

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CardUidTest {

    @Test
    fun `fromBytes empty returns empty CardUid`() {
        assertEquals(CardUid(""), CardUid.fromBytes(ByteArray(0)))
        assertEquals("", CardUid.fromBytes(ByteArray(0)).hex)
    }

    @Test
    fun `fromBytes single zero byte`() {
        assertEquals(CardUid("00"), CardUid.fromBytes(byteArrayOf(0x00)))
    }

    @Test
    fun `fromBytes single low-nybble byte preserves leading zero`() {
        assertEquals(CardUid("0f"), CardUid.fromBytes(byteArrayOf(0x0F)))
    }

    @Test
    fun `fromBytes single high-byte 0xFF`() {
        assertEquals(CardUid("ff"), CardUid.fromBytes(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun `fromBytes single byte 0x4A maps to lowercase 4a`() {
        assertEquals(CardUid("4a"), CardUid.fromBytes(byteArrayOf(0x4A)))
    }

    @Test
    fun `fromBytes 4-byte UID`() {
        val bytes = byteArrayOf(0x04, 0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte())
        assertEquals(CardUid("04a1b2c3"), CardUid.fromBytes(bytes))
    }

    @Test
    fun `fromBytes 7-byte UID`() {
        val bytes = byteArrayOf(
            0x04, 0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte(),
            0xD4.toByte(), 0xE5.toByte(), 0x80.toByte(),
        )
        assertEquals(CardUid("04a1b2c3d4e580"), CardUid.fromBytes(bytes))
    }

    @Test
    fun `fromBytes 10-byte UID`() {
        val bytes = byteArrayOf(
            0x01, 0x02, 0x03, 0x04, 0x05,
            0x06, 0x07, 0x08, 0x09, 0x0A,
        )
        assertEquals(CardUid("0102030405060708090a"), CardUid.fromBytes(bytes))
    }

    @Test
    fun `toString returns hex verbatim`() {
        assertEquals("04a1b2c3", CardUid("04a1b2c3").toString())
        assertEquals("", CardUid("").toString())
    }

    @Test
    fun `equality on same lowercase hex`() {
        assertEquals(CardUid("04a1b2"), CardUid("04a1b2"))
    }

    @Test
    fun `equality is case sensitive on raw constructor input`() {
        // Documented fragility per BR-U2-CU-5: only the encoder/decoder
        // normalises to lowercase. Constructing directly with uppercase
        // produces a CardUid that compares unequal.
        assertNotEquals(CardUid("04a1b2"), CardUid("04A1B2"))
    }

    @Test
    fun `fromBytes then construct-from-string yields equal CardUid`() {
        val a = CardUid.fromBytes(byteArrayOf(0x04, 0xA1.toByte(), 0xB2.toByte()))
        val b = CardUid("04a1b2")
        assertEquals(a, b)
    }
}
