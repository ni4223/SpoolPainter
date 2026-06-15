package com.spoolpainter.app.ui.whatsnew

import com.spoolpainter.app.data.local.Settings
import com.spoolpainter.app.support.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WhatsNewControllerTest {

    private fun controller(
        settings: FakeSettingsRepository,
        scope: kotlinx.coroutines.CoroutineScope,
    ) = WhatsNewController(settings, scope)

    // --- pure trigger logic -------------------------------------------------

    @Test
    fun `fresh install with no record is suppressed`() {
        assertFalse(
            WhatsNewController.shouldShow(
                currentVersion = 107,
                lastSeenVersion = 0,
                isFreshInstall = true,
            ),
        )
    }

    @Test
    fun `v1 to v2 updater with no record is shown`() {
        assertTrue(
            WhatsNewController.shouldShow(
                currentVersion = 107,
                lastSeenVersion = 0,
                isFreshInstall = false,
            ),
        )
    }

    @Test
    fun `v2 point release updater is shown`() {
        assertTrue(
            WhatsNewController.shouldShow(
                currentVersion = 107,
                lastSeenVersion = 106,
                isFreshInstall = false,
            ),
        )
    }

    @Test
    fun `same version relaunch is not shown`() {
        assertFalse(
            WhatsNewController.shouldShow(
                currentVersion = 107,
                lastSeenVersion = 107,
                isFreshInstall = false,
            ),
        )
    }

    @Test
    fun `downgrade or already ahead is not shown`() {
        assertFalse(
            WhatsNewController.shouldShow(
                currentVersion = 106,
                lastSeenVersion = 107,
                isFreshInstall = false,
            ),
        )
    }

    // --- onColdStart wiring --------------------------------------------------

    @Test
    fun `onColdStart shows sheet for in-place updater and marks seen on dismiss`() = runTest {
        val settings = FakeSettingsRepository()
        val controller = controller(settings, this)

        controller.onColdStart(currentVersion = 107, isFreshInstall = false)
        assertTrue(controller.visible.value)
        // Not marked seen until dismissed.
        assertEquals(0, settings.settings.value.lastSeenWhatsNewVersion)

        controller.onDismiss()
        advanceUntilIdle()
        assertFalse(controller.visible.value)
        assertEquals(107, settings.settings.value.lastSeenWhatsNewVersion)
    }

    @Test
    fun `onColdStart suppresses fresh install but still records version`() = runTest {
        val settings = FakeSettingsRepository()
        val controller = controller(settings, this)

        controller.onColdStart(currentVersion = 107, isFreshInstall = true)
        advanceUntilIdle()
        assertFalse(controller.visible.value)
        // Recorded so a later same-version relaunch never re-evaluates to show.
        assertEquals(107, settings.settings.value.lastSeenWhatsNewVersion)
    }

    @Test
    fun `onColdStart does not show when already seen current version`() = runTest {
        val settings = FakeSettingsRepository(Settings(lastSeenWhatsNewVersion = 107))
        val controller = controller(settings, this)

        controller.onColdStart(currentVersion = 107, isFreshInstall = false)
        advanceUntilIdle()
        assertFalse(controller.visible.value)
    }
}
