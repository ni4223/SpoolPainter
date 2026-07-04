package com.spoolpainter.app.domain.primitives

/**
 * Pure color-sampling math for the U17 camera color picker.
 *
 * Kept free of Android UI / hardware types so it is unit-testable on the JVM.
 * Pixels are ARGB ints (as produced by `Bitmap.getPixel` / a CameraX frame
 * converted to a bitmap); the alpha channel is ignored. Output is an uppercase
 * 6-char RGB hex with no leading `#`, matching the convention every other
 * SpoolPainter color path uses (see ColorPicker / FormMapping / ColorHexCodec).
 */
object ColorSampling {

    /** Default edge length of the square sample patch, in pixels. */
    const val DEFAULT_PATCH: Int = 20

    /**
     * Reticle diameter as a fraction of the frame's shorter side. The camera
     * preview uses FILL_CENTER (center-crop), so sizing both the on-screen
     * reticle AND the sampled patch to the same fraction of their respective
     * shorter sides keeps them aligned: the square patch is inscribed in the
     * circle the user aims with, so everything sampled is visibly inside the
     * ring (WYSIWYG — UI-47).
     */
    const val DEFAULT_PATCH_FRACTION: Float = 0.10f

    /**
     * Square patch edge in pixels for a frame whose shorter side is
     * [frameShortSide], sized to [fraction] of that side. Floors at 1px so a
     * degenerate frame still yields a sampleable region.
     */
    fun patchForFraction(
        frameShortSide: Int,
        fraction: Float = DEFAULT_PATCH_FRACTION,
    ): Int {
        require(frameShortSide > 0) { "frameShortSide must be positive" }
        return (frameShortSide * fraction).toInt().coerceAtLeast(1)
    }

    /**
     * Inclusive-exclusive pixel bounds of an [patch]×[patch] square centered in
     * an image of [width]×[height], clamped so it never runs off an edge.
     * Returns (left, top, right, bottom); right/bottom are exclusive.
     */
    fun patchBounds(
        width: Int,
        height: Int,
        patch: Int = DEFAULT_PATCH,
    ): IntArray {
        require(width > 0 && height > 0) { "image must be non-empty" }
        val half = patch.coerceAtLeast(1) / 2
        val cx = width / 2
        val cy = height / 2
        val left = (cx - half).coerceIn(0, width - 1)
        val top = (cy - half).coerceIn(0, height - 1)
        val right = (cx + half).coerceIn(left + 1, width)
        val bottom = (cy + half).coerceIn(top + 1, height)
        return intArrayOf(left, top, right, bottom)
    }

    /**
     * Average the R/G/B channels of the given ARGB [pixels] laid out row-major
     * in a [width]×[height] grid, over the center [patch]×[patch] block, and
     * return the mean as a 6-char uppercase hex string.
     *
     * @throws IllegalArgumentException if [pixels] is smaller than width*height.
     */
    fun averageHex(
        pixels: IntArray,
        width: Int,
        height: Int,
        patch: Int = DEFAULT_PATCH,
    ): String {
        require(pixels.size >= width * height) { "pixels too small for $width×$height" }
        val (left, top, right, bottom) = patchBounds(width, height, patch)
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0L
        for (y in top until bottom) {
            val rowStart = y * width
            for (x in left until right) {
                val argb = pixels[rowStart + x]
                sumR += (argb shr 16) and 0xFF
                sumG += (argb shr 8) and 0xFF
                sumB += argb and 0xFF
                count++
            }
        }
        // patchBounds guarantees a non-empty region, but guard defensively.
        if (count == 0L) return "000000"
        val r = (sumR / count).toInt()
        val g = (sumG / count).toInt()
        val b = (sumB / count).toInt()
        return toHex(r, g, b)
    }

    /** Format three 0..255 channel values as a 6-char uppercase RGB hex. */
    fun toHex(r: Int, g: Int, b: Int): String {
        val rr = r.coerceIn(0, 255)
        val gg = g.coerceIn(0, 255)
        val bb = b.coerceIn(0, 255)
        val packed = (rr shl 16) or (gg shl 8) or bb
        return packed.toString(16).uppercase().padStart(6, '0')
    }
}
