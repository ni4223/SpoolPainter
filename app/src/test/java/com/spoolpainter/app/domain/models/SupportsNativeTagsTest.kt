package com.spoolpainter.app.domain.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Native tag API shipped in Spoolman 0.27.0 — the gate parses the
 * /api/v1/info version tolerantly (pre-release suffixes compare by numeric
 * prefix) and fails closed on anything unparseable.
 */
class SupportsNativeTagsTest {

    @Test
    fun `0_27_0 exact minimum supported`() {
        assertTrue(SpoolmanInfo(version = "0.27.0").supportsNativeTags())
    }

    @Test
    fun `0_27_1 supported`() {
        assertTrue(SpoolmanInfo(version = "0.27.1").supportsNativeTags())
    }

    @Test
    fun `future major version supported`() {
        assertTrue(SpoolmanInfo(version = "1.0.0").supportsNativeTags())
        assertTrue(SpoolmanInfo(version = "1.2.0").supportsNativeTags())
    }

    @Test
    fun `0_26_1 below minimum not supported`() {
        assertFalse(SpoolmanInfo(version = "0.26.1").supportsNativeTags())
    }

    @Test
    fun `pre-release of minimum supported`() {
        assertTrue(SpoolmanInfo(version = "0.27.0-beta").supportsNativeTags())
        assertTrue(SpoolmanInfo(version = "0.27.0rc1").supportsNativeTags())
    }

    @Test
    fun `pre-release below minimum not supported`() {
        assertFalse(SpoolmanInfo(version = "0.26.9-beta").supportsNativeTags())
    }

    @Test
    fun `null version not supported`() {
        assertFalse(SpoolmanInfo(version = null).supportsNativeTags())
    }

    @Test
    fun `null receiver not supported`() {
        val info: SpoolmanInfo? = null
        assertFalse(info.supportsNativeTags())
    }

    @Test
    fun `garbage version not supported`() {
        assertFalse(SpoolmanInfo(version = "garbage").supportsNativeTags())
        assertFalse(SpoolmanInfo(version = "").supportsNativeTags())
        assertFalse(SpoolmanInfo(version = "   ").supportsNativeTags())
        assertFalse(SpoolmanInfo(version = "not.a.version").supportsNativeTags())
    }

    @Test
    fun `short numeric version compares padded with zeros`() {
        // "0.27" is unknown at patch level; padded to 0.27.0 it passes.
        assertTrue(SpoolmanInfo(version = "0.27").supportsNativeTags())
        assertFalse(SpoolmanInfo(version = "0.26").supportsNativeTags())
    }
}
