package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.data.local.TagStorageMode
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanInfo
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.TagSyncConflict
import com.spoolpainter.app.domain.models.TagSyncState
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.ExtraCardUidsCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/** Automatic legacy→native mirror (issue #19 Phase 4): one bulk diff plus
 *  idempotent linkTag calls for the missing UIDs only. Never steals on
 *  409, never writes card_uids, and skips UIDs whose link fails so the
 *  rest of the run still lands. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryTagSyncTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val uidA = CardUid("AABBCCDD")
    private val uidB = CardUid("11223344")

    private fun seedSpool(api: FakeSpoolmanApi, id: Int, cardUids: List<CardUid>) {
        api.spoolList += SpoolmanSpool(
            id = id,
            filament = SpoolmanFilament(id = id, material = "PLA"),
            extra = mapOf("card_uids" to ExtraCardUidsCodec.encode(cardUids)),
        )
    }

    /** Probes the info endpoint; the mirror fires inline on the harness's
     *  unconfined scope, so it has already completed when this returns.
     *  The call log is deliberately NOT cleared — the assertions target
     *  the mirror's own calls. */
    private suspend fun SpoolmanRepositoryHarness.probeNativeServer(version: String = "0.27.0") {
        fakeApi.info = SpoolmanInfo(version = version)
        repository.testConnection()
    }

    private fun linkTagCalls(api: FakeSpoolmanApi): List<String> =
        api.callLog.filter { it.startsWith("linkTag") }

    @Test
    fun `sync mirrors only the missing uid and a second run links nothing`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, cardUids = listOf(uidA, uidB))
        h.fakeApi.spoolTags["AABBCCDD"] = 1
        h.repository.refresh()
        val cachedTagsBeforeSync = h.repository.spools.value.first { it.id == 1 }.tags

        h.probeNativeServer()

        assertEquals(listOf("linkTag(1,uid=11223344)"), linkTagCalls(h.fakeApi))
        assertEquals(1, h.fakeApi.spoolTags["11223344"])
        assertEquals(
            TagSyncState.Finished(mirrored = 1, conflicts = emptyList(), failed = 0),
            h.repository.tagSyncState.value,
        )
        // Side read only: the cache still reflects the pre-sync snapshot.
        assertEquals(cachedTagsBeforeSync, h.repository.spools.value.first { it.id == 1 }.tags)

        h.fakeApi.callLog.clear()
        h.repository.requestTagSync()

        assertTrue(linkTagCalls(h.fakeApi).isEmpty())
        assertEquals(
            TagSyncState.Finished(mirrored = 0, conflicts = emptyList(), failed = 0),
            h.repository.tagSyncState.value,
        )
    }

    @Test
    fun `sync skips a uid held elsewhere and records the holder without stealing`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, cardUids = listOf(uidA))
        seedSpool(h.fakeApi, 2, cardUids = listOf(uidA))

        h.probeNativeServer()

        assertEquals(listOf("linkTag(1,uid=AABBCCDD)", "linkTag(2,uid=AABBCCDD)"), linkTagCalls(h.fakeApi))
        assertEquals(
            TagSyncState.Finished(
                mirrored = 1,
                conflicts = listOf(TagSyncConflict(uid = "AABBCCDD", targetSpoolId = 2, holderSpoolId = 1)),
                failed = 0,
            ),
            h.repository.tagSyncState.value,
        )
        // The first spool kept the UID; the second was not satisfied by a move.
        assertEquals(1, h.fakeApi.spoolTags["AABBCCDD"])
        // Mirror-only: no spool PATCH, card_uids untouched on both spools.
        assertTrue(h.fakeApi.callLog.none { it.startsWith("patchSpool") })
        assertEquals(
            "\"AABBCCDD\"",
            h.fakeApi.spoolList.first { it.id == 1 }.extra?.get("card_uids"),
        )
        assertEquals(
            "\"AABBCCDD\"",
            h.fakeApi.spoolList.first { it.id == 2 }.extra?.get("card_uids"),
        )

        h.fakeApi.callLog.clear()
        h.repository.requestTagSync()

        assertEquals(listOf("linkTag(2,uid=AABBCCDD)"), linkTagCalls(h.fakeApi))
        assertEquals(1, h.fakeApi.spoolTags["AABBCCDD"])
    }

    @Test
    fun `sync is a no-op in LEGACY_ONLY`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, cardUids = listOf(uidA))
        h.settingsRepository.setTagStorageMode(TagStorageMode.LEGACY_ONLY)

        h.probeNativeServer()
        h.repository.requestTagSync()

        assertTrue(linkTagCalls(h.fakeApi).isEmpty())
        assertEquals(TagSyncState.Idle, h.repository.tagSyncState.value)
    }

    @Test
    fun `sync is a no-op on a server older than 0_27`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, cardUids = listOf(uidA))

        h.probeNativeServer(version = "0.1.0")
        h.repository.requestTagSync()

        assertTrue(linkTagCalls(h.fakeApi).isEmpty())
        assertEquals(TagSyncState.Idle, h.repository.tagSyncState.value)
    }

    @Test
    fun `sync is a no-op without a configured url`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder, initialUrl = "")
        seedSpool(h.fakeApi, 1, cardUids = listOf(uidA))
        h.fakeApi.info = SpoolmanInfo(version = "0.27.0")

        h.repository.requestTagSync()

        assertTrue(h.fakeApi.callLog.isEmpty())
        assertEquals(TagSyncState.Idle, h.repository.tagSyncState.value)
    }

    @Test
    fun `a failed linkTag counts as failed while later uids still sync`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, cardUids = listOf(uidA, uidB))
        h.fakeApi.nextLinkTagFailure = FakeSpoolmanApi.Failure.Throws(IOException("flaky"))

        h.probeNativeServer()

        assertEquals(
            listOf("linkTag(1,uid=AABBCCDD)", "linkTag(1,uid=11223344)"),
            linkTagCalls(h.fakeApi),
        )
        assertEquals(
            TagSyncState.Finished(mirrored = 1, conflicts = emptyList(), failed = 1),
            h.repository.tagSyncState.value,
        )
        assertEquals(1, h.fakeApi.spoolTags["11223344"])
    }
}
