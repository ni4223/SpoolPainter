package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.data.local.TagStorageMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Mode gating of [SpoolmanRepository.ensureExtraFieldsRegistered]:
 *  NATIVE_ONLY skips the spool card_uids field (card UIDs live in the
 *  native tag store) but still registers the filament variant field. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryEnsureFieldsModeTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `NATIVE_ONLY skips spool card_uids but registers filament variant`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.settingsRepository.setTagStorageMode(TagStorageMode.NATIVE_ONLY)

        val outcome = h.repository.ensureExtraFieldsRegistered()

        assertEquals(SpoolmanOutcome.Success(Unit), outcome)
        assertTrue("variant" in h.fakeApi.filamentExtraFields)
        assertTrue(h.fakeApi.spoolExtraFields.isEmpty())
        // The spool side isn't even probed.
        assertTrue(h.fakeApi.callLog.none { it == "listFields(spool)" })
        assertTrue(h.fakeApi.callLog.none { it.startsWith("postField(spool") })
        assertTrue(h.fakeApi.callLog.contains("postField(filament,variant)"))
    }

    @Test
    fun `BOTH registers spool card_uids as before`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        // Default mode is BOTH.

        val outcome = h.repository.ensureExtraFieldsRegistered()

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertTrue("card_uids" in h.fakeApi.spoolExtraFields)
        assertTrue("variant" in h.fakeApi.filamentExtraFields)
    }

    @Test
    fun `LEGACY_ONLY registers spool card_uids as before`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.settingsRepository.setTagStorageMode(TagStorageMode.LEGACY_ONLY)

        val outcome = h.repository.ensureExtraFieldsRegistered()

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertTrue("card_uids" in h.fakeApi.spoolExtraFields)
        assertTrue("variant" in h.fakeApi.filamentExtraFields)
    }
}
