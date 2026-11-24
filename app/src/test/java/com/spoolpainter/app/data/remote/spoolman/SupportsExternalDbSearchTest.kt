package com.spoolpainter.app.data.remote.spoolman

import com.google.gson.Gson
import com.spoolpainter.app.domain.models.SpoolmanInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * External filament DB search shipped in Spoolman 0.26.0 — the gate parses
 * the /api/v1/info version and fails closed on anything unparseable.
 */
class SupportsExternalDbSearchTest {

    @Test
    fun `0_27_1 supported`() {
        assertTrue(SpoolmanInfo(version = "0.27.1").supportsExternalDbSearch())
    }

    @Test
    fun `0_26_0 exact minimum supported`() {
        assertTrue(SpoolmanInfo(version = "0.26.0").supportsExternalDbSearch())
    }

    @Test
    fun `0_25_9 below minimum not supported`() {
        assertFalse(SpoolmanInfo(version = "0.25.9").supportsExternalDbSearch())
    }

    @Test
    fun `future major version supported`() {
        assertTrue(SpoolmanInfo(version = "1.2.3").supportsExternalDbSearch())
    }

    @Test
    fun `null version not supported`() {
        assertFalse(SpoolmanInfo(version = null).supportsExternalDbSearch())
    }

    @Test
    fun `null receiver not supported`() {
        val info: SpoolmanInfo? = null
        assertFalse(info.supportsExternalDbSearch())
    }

    @Test
    fun `garbage version not supported`() {
        assertFalse(SpoolmanInfo(version = "garbage").supportsExternalDbSearch())
        assertFalse(SpoolmanInfo(version = "not.a.version").supportsExternalDbSearch())
    }

    @Test
    fun `short version not supported`() {
        assertFalse(SpoolmanInfo(version = "0.27").supportsExternalDbSearch())
        assertFalse(SpoolmanInfo(version = "").supportsExternalDbSearch())
    }

    @Test
    fun `pre-release suffix not supported`() {
        // "0-beta" doesn't parse as an int patch → fail closed.
        assertFalse(SpoolmanInfo(version = "0.26.0-beta").supportsExternalDbSearch())
    }

    @Test
    fun `info payload parses external_db_name alongside version`() {
        val info = Gson().fromJson(
            """{"version": "0.27.1", "external_db_name": "SpoolmanDB"}""",
            SpoolmanInfo::class.java,
        )
        assertEquals("0.27.1", info.version)
        assertEquals("SpoolmanDB", info.external_db_name)
        assertTrue(info.supportsExternalDbSearch())
    }
}
