package com.spoolpainter.app.ui.whatsnew

import com.spoolpainter.app.data.local.Settings
import com.spoolpainter.app.support.FakeSettingsRepository
import com.spoolpainter.app.ui.components.sheets.WHATS_NEW_CONTENT_VERSION
import com.spoolpainter.app.ui.components.sheets.whatsNewV2Highlights
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
                contentVersion = 107,
                lastSeenVersion = 0,
                isFreshInstall = true,
            ),
        )
    }

    @Test
    fun `v1 to v2 updater with no record is shown`() {
        assertTrue(
            WhatsNewController.shouldShow(
                contentVersion = 107,
                lastSeenVersion = 0,
                isFreshInstall = false,
            ),
        )
    }

    @Test
    fun `v2 point release updater is shown`() {
        assertTrue(
            WhatsNewController.shouldShow(
                contentVersion = 107,
                lastSeenVersion = 106,
                isFreshInstall = false,
            ),
        )
    }

    @Test
    fun `same version relaunch is not shown`() {
        assertFalse(
            WhatsNewController.shouldShow(
                contentVersion = 107,
                lastSeenVersion = 107,
                isFreshInstall = false,
            ),
        )
    }

    @Test
    fun `downgrade or already ahead is not shown`() {
        assertFalse(
            WhatsNewController.shouldShow(
                contentVersion = 106,
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

        controller.onColdStart(contentVersion = 107, isFreshInstall = false)
        advanceUntilIdle()
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

        controller.onColdStart(contentVersion = 107, isFreshInstall = true)
        advanceUntilIdle()
        assertFalse(controller.visible.value)
        // Recorded so a later same-version relaunch never re-evaluates to show.
        assertEquals(107, settings.settings.value.lastSeenWhatsNewVersion)
    }

    @Test
    fun `onColdStart does not show when already seen current version`() = runTest {
        val settings = FakeSettingsRepository(Settings(lastSeenWhatsNewVersion = 107))
        val controller = controller(settings, this)

        controller.onColdStart(contentVersion = 107, isFreshInstall = false)
        advanceUntilIdle()
        assertFalse(controller.visible.value)
    }
    @Test
    fun `an app update that changes no copy does not re-show (regression)`() {
        // The bug: the trigger compared against the app's versionCode, so
        // bumping 112 -> 115 re-opened the sheet on copy the user had already
        // dismissed at 112. Keyed to the content version, an app-only bump is
        // silent.
        assertFalse(
            WhatsNewController.shouldShow(
                contentVersion = 110,
                lastSeenVersion = 112,
                isFreshInstall = false,
            ),
        )
    }

    @Test
    fun `a user behind the last copy change is still shown once`() {
        // Someone on 108 never saw the camera row added at 110.
        assertTrue(
            WhatsNewController.shouldShow(
                contentVersion = 110,
                lastSeenVersion = 108,
                isFreshInstall = false,
            ),
        )
    }

    @Test
    fun `shipped content version matches the highlights it gates`() {
        // Guards the pairing the fix depends on: if the copy is edited without
        // bumping the constant, nobody is told. Update BOTH, together.
        assertEquals(110, WHATS_NEW_CONTENT_VERSION)
        assertEquals(6, whatsNewV2Highlights.size)
    }
}
