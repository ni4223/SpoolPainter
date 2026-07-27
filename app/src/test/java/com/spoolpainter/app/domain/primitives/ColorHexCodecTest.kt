package com.spoolpainter.app.domain.primitives

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorHexCodecTest {

    @Test
    fun `canonicalise strips hash and uppercases`() {
        assertEquals("FF0000", ColorHexCodec.canonicalise("#ff0000"))
        assertEquals("00FF00", ColorHexCodec.canonicalise("00ff00"))
    }

    @Test
    fun `canonicalise takes the last 6 of an 8-digit ARGB`() {
        assertEquals("FF0000", ColorHexCodec.canonicalise("ffff0000"))
    }

    @Test
    fun `canonicalise returns null for null or empty`() {
        assertNull(ColorHexCodec.canonicalise(null))
        assertNull(ColorHexCodec.canonicalise(""))
        assertNull(ColorHexCodec.canonicalise("#"))
    }

    @Test
    fun `toRgb decodes primary colors`() {
        assertEquals(Triple(255, 0, 0), ColorHexCodec.toRgb("FF0000"))
        assertEquals(Triple(0, 255, 0), ColorHexCodec.toRgb("00FF00"))
        assertEquals(Triple(0, 0, 255), ColorHexCodec.toRgb("0000FF"))
    }

    @Test
    fun `toRgb handles hash prefix and lowercase`() {
        assertEquals(Triple(18, 52, 86), ColorHexCodec.toRgb("#123456"))
    }

    @Test
    fun `toRgb takes the last 6 of an 8-digit ARGB`() {
        assertEquals(Triple(255, 0, 0), ColorHexCodec.toRgb("ffff0000"))
    }

    @Test
    fun `toRgb black and white`() {
        assertEquals(Triple(0, 0, 0), ColorHexCodec.toRgb("000000"))
        assertEquals(Triple(255, 255, 255), ColorHexCodec.toRgb("FFFFFF"))
    }

    @Test
    fun `toRgb returns null for null blank or malformed`() {
        assertNull(ColorHexCodec.toRgb(null))
        assertNull(ColorHexCodec.toRgb(""))
        assertNull(ColorHexCodec.toRgb("12345"))   // 5 digits
        assertNull(ColorHexCodec.toRgb("GGGGGG"))  // not hex
        assertNull(ColorHexCodec.toRgb("red"))
    }
}
