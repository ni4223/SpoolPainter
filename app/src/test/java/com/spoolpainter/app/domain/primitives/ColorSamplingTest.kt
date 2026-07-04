package com.spoolpainter.app.domain.primitives

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSamplingTest {

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `uniform patch returns that color`() {
        val red = argb(255, 0, 0)
        val pixels = IntArray(10 * 10) { red }
        assertEquals("FF0000", ColorSampling.averageHex(pixels, 10, 10, patch = 4))
    }

    @Test
    fun `mixed patch averages the channels`() {
        // 2x2 image, patch covers all four: black, white, red, blue.
        // Mean R = (0+255+255+0)/4 = 127; G = (0+255+0+0)/4 = 63; B = (0+255+0+255)/4 = 127.
        val pixels = intArrayOf(
            argb(0, 0, 0), argb(255, 255, 255),
            argb(255, 0, 0), argb(0, 0, 255),
        )
        assertEquals("7F3F7F", ColorSampling.averageHex(pixels, 2, 2, patch = 2))
    }

    @Test
    fun `patch clamps to image bounds near a corner`() {
        // 3x3 image; a huge patch clamps to the whole image, not out of range.
        val pixels = IntArray(3 * 3) { argb(16, 32, 48) }
        assertEquals("102030", ColorSampling.averageHex(pixels, 3, 3, patch = 999))
    }

    @Test
    fun `pure black`() {
        val pixels = IntArray(4) { argb(0, 0, 0) }
        assertEquals("000000", ColorSampling.averageHex(pixels, 2, 2, patch = 2))
    }

    @Test
    fun `pure white`() {
        val pixels = IntArray(4) { argb(255, 255, 255) }
        assertEquals("FFFFFF", ColorSampling.averageHex(pixels, 2, 2, patch = 2))
    }

    @Test
    fun `toHex zero-pads short values`() {
        assertEquals("010203", ColorSampling.toHex(1, 2, 3))
        assertEquals("000000", ColorSampling.toHex(0, 0, 0))
    }

    @Test
    fun `toHex clamps out-of-range channels`() {
        assertEquals("FF00FF", ColorSampling.toHex(300, -5, 255))
    }

    @Test
    fun `patchBounds is centered and non-empty`() {
        val (left, top, right, bottom) = ColorSampling.patchBounds(100, 80, patch = 20)
        assertEquals(40, left)
        assertEquals(30, top)
        assertEquals(60, right)
        assertEquals(50, bottom)
    }
}
