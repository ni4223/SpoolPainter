package com.spoolpainter.app.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the referral link data. These URLs are frozen into a shipped APK until
 * the next release, so a typo is not editable the way the README is; and a
 * dropped `ref` parameter silently costs attribution without breaking anything
 * visible, which is the failure mode nobody would notice.
 */
class ReferralTargetsTest {

    @Test
    fun `every target carries a referral marker`() {
        REFERRAL_TARGETS.forEach { target ->
            val marker = target.url.contains("ref=", ignoreCase = true) ||
                target.url.contains("/NI42", ignoreCase = true)
            assertTrue("${target.label} has no referral marker: ${target.url}", marker)
        }
    }

    @Test
    fun `every target is https`() {
        REFERRAL_TARGETS.forEach {
            assertTrue("${it.label} is not https: ${it.url}", it.url.startsWith("https://"))
        }
    }

    @Test
    fun `labels are unique so the test tags they derive from are unique`() {
        val tags = REFERRAL_TARGETS.map { it.label.lowercase().replace(' ', '-') }
        assertEquals(tags.size, tags.distinct().size)
    }

    @Test
    fun `polymaker is a link only and every snapmaker region carries the code`() {
        assertEquals(null, POLYMAKER_TARGET.code)
        SNAPMAKER_TARGETS.forEach { assertEquals("ni42", it.code) }
    }

    @Test
    fun `all three snapmaker regions are present and point at distinct stores`() {
        assertEquals(listOf("US", "EU", "Global"), SNAPMAKER_TARGETS.map { it.label })
        assertEquals(3, SNAPMAKER_TARGETS.map { it.url }.distinct().size)
    }

    @Test
    fun `every target carries a brand accent`() {
        // Guards against a target added without one, which would render an
        // invisible outline rather than failing loudly.
        REFERRAL_TARGETS.forEach {
            assertTrue("${it.label} has a transparent accent", it.accent.alpha > 0f)
        }
    }
}
