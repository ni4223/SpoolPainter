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

    // U33: Spoolman stores alpha at the end (RRGGBBAA).
    @Test
    fun `canonicalise takes the first 6 of an 8-digit RRGGBBAA`() {
        assertEquals("FF0000", ColorHexCodec.canonicalise("ff000080"))
        assertEquals("FFFFFF", ColorHexCodec.canonicalise("FFFFFF80"))
        assertEquals("FF0000", ColorHexCodec.canonicalise("#FF000080"))
    }

    @Test
    fun `alpha reads the last 2 of an 8-digit colour only`() {
        assertEquals("80", ColorHexCodec.alpha("FF000080"))
        assertEquals("0A", ColorHexCodec.alpha("#ff00000a"))
        assertNull(ColorHexCodec.alpha("FF0000"))
        assertNull(ColorHexCodec.alpha(null))
        assertNull(ColorHexCodec.alpha("FF00008"))
        assertNull(ColorHexCodec.alpha("GG000080"))
    }

    @Test
    fun `overrideFor sends nothing when the RGB is unchanged`() {
        assertNull(ColorHexCodec.overrideFor("FF0000", "FF000080"))
        assertNull(ColorHexCodec.overrideFor("ff0000", "FF0000"))
    }

    @Test
    fun `overrideFor keeps the stored alpha on a new colour`() {
        assertEquals("00FF0080", ColorHexCodec.overrideFor("00FF00", "FF000080"))
    }

    @Test
    fun `overrideFor sends plain RGB when nothing is stored or stored is opaque`() {
        assertEquals("00FF00", ColorHexCodec.overrideFor("00ff00", "FF0000"))
        assertEquals("00FF00", ColorHexCodec.overrideFor("00FF00", null))
    }

    @Test
    fun `overrideFor sends nothing for an empty or invalid request`() {
        assertNull(ColorHexCodec.overrideFor(null, "FF000080"))
        assertNull(ColorHexCodec.overrideFor("", "FF000080"))
        assertNull(ColorHexCodec.overrideFor("12345", "FF000080"))
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
    fun `toRgb takes the first 6 of an 8-digit RRGGBBAA`() {
        assertEquals(Triple(255, 0, 0), ColorHexCodec.toRgb("FF000080"))
        assertEquals(Triple(255, 255, 255), ColorHexCodec.toRgb("FFFFFF80"))
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
