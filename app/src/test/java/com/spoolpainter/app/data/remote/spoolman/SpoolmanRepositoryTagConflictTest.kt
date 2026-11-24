package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.data.local.TagStorageMode
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanInfo
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Append 409 whose conflict body has no `spool_id` (the UID is held by a
 *  filament / any non-spool holder): surfaced as a distinct held-elsewhere
 *  error — no automatic move is attempted. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryTagConflictTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val uid = CardUid("AABBCCDD")

    private fun seedSpool(api: FakeSpoolmanApi, id: Int) {
        api.spoolList += SpoolmanSpool(
            id = id,
            filament = SpoolmanFilament(id = id, material = "PLA"),
        )
    }

    private suspend fun SpoolmanRepositoryHarness.enableNativeServer() {
        fakeApi.info = SpoolmanInfo(version = "0.27.0")
        repository.testConnection()
        fakeApi.callLog.clear()
    }

    private fun filamentHolderConflict(): FakeSpoolmanApi.Failure.Http =
        FakeSpoolmanApi.Failure.Http(
            409,
            """{"message":"Tag AABBCCDD is already used by filament 5","filament_id":5}""",
        )

    @Test
    fun `append 409 with filament holder in BOTH surfaces distinct held-elsewhere error`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1)
        h.enableNativeServer()
        h.fakeApi.failLinkTag = filamentHolderConflict()

        val outcome = h.repository.appendCardUidToSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.HttpError)
        val error = outcome as SpoolmanOutcome.HttpError
        assertEquals(409, error.code)
        assertTrue("message was: ${error.message}", error.message.contains("held elsewhere"))
        // The legacy write already landed before the conflict surfaced.
        assertEquals(
            "\"AABBCCDD\"",
            h.fakeApi.spoolList.first { it.id == 1 }.extra?.get("card_uids"),
        )
        // No move was attempted against a non-spool holder.
        assertTrue(h.fakeApi.callLog.none { it.startsWith("unlinkTag") })
    }

    @Test
    fun `append 409 with filament holder in NATIVE_ONLY surfaces distinct error`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1)
        h.settingsRepository.setTagStorageMode(TagStorageMode.NATIVE_ONLY)
        h.enableNativeServer()
        h.fakeApi.failLinkTag = filamentHolderConflict()

        val outcome = h.repository.appendCardUidToSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.HttpError)
        val error = outcome as SpoolmanOutcome.HttpError
        assertEquals(409, error.code)
        assertTrue("message was: ${error.message}", error.message.contains("held elsewhere"))
        assertTrue(h.fakeApi.spoolTags.isEmpty())
        assertTrue(h.fakeApi.callLog.none { it.startsWith("patchSpool") })
        assertTrue(h.fakeApi.callLog.none { it.startsWith("unlinkTag") })
    }
}
