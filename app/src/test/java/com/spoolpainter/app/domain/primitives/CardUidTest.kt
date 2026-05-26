package com.spoolpainter.app.domain.primitives

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
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
        assertEquals(CardUid("0F"), CardUid.fromBytes(byteArrayOf(0x0F)))
    }

    @Test
    fun `fromBytes single high-byte 0xFF`() {
        assertEquals(CardUid("FF"), CardUid.fromBytes(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun `fromBytes single byte 0x4A maps to uppercase 4A`() {
        assertEquals(CardUid("4A"), CardUid.fromBytes(byteArrayOf(0x4A)))
    }

    @Test
    fun `fromBytes 4-byte UID`() {
        val bytes = byteArrayOf(0x04, 0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte())
        assertEquals(CardUid("04A1B2C3"), CardUid.fromBytes(bytes))
    }

    @Test
    fun `fromBytes 7-byte UID`() {
        val bytes = byteArrayOf(
            0x04, 0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte(),
            0xD4.toByte(), 0xE5.toByte(), 0x80.toByte(),
        )
        assertEquals(CardUid("04A1B2C3D4E580"), CardUid.fromBytes(bytes))
    }

    @Test
    fun `fromBytes 10-byte UID`() {
        val bytes = byteArrayOf(
            0x01, 0x02, 0x03, 0x04, 0x05,
            0x06, 0x07, 0x08, 0x09, 0x0A,
        )
        assertEquals(CardUid("0102030405060708090A"), CardUid.fromBytes(bytes))
    }

    @Test
    fun `toString returns hex verbatim`() {
        assertEquals("04A1B2C3", CardUid("04A1B2C3").toString())
        assertEquals("", CardUid("").toString())
    }

    @Test
    fun `equality on same uppercase hex`() {
        assertEquals(CardUid("04A1B2"), CardUid("04A1B2"))
    }

    @Test
    fun `equality is case sensitive on raw constructor input`() {
        // Documented fragility: the value class wraps the hex string verbatim.
        // Normalisation is the responsibility of CardUid.fromBytes / normaliseHex.
        assertNotEquals(CardUid("04a1b2"), CardUid("04A1B2"))
    }

    @Test
    fun `fromBytes then construct-from-string yields equal CardUid`() {
        val a = CardUid.fromBytes(byteArrayOf(0x04, 0xA1.toByte(), 0xB2.toByte()))
        val b = CardUid("04A1B2")
        assertEquals(a, b)
    }

    @Test
    fun `normaliseHex uppercases valid input`() {
        assertEquals("AABBCCDD", CardUid.normaliseHex("aabbccdd"))
    }

    @Test
    fun `normaliseHex passes already uppercase input`() {
        assertEquals("AABBCCDD", CardUid.normaliseHex("AABBCCDD"))
    }

    @Test
    fun `normaliseHex throws on non hex`() {
        assertThrows(IllegalArgumentException::class.java) {
            CardUid.normaliseHex("AABBCC??")
        }
    }
}
