package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.data.local.TagStorageMode
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanInfo
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.ExtraCardUidsCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Per-mode dispatch of [SpoolmanRepository.findSpoolsByCardUid]: union +
 *  dedupe (native hit first) in BOTH, tag-store-only in NATIVE_ONLY,
 *  legacy scan on old servers. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryFindTagModeTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val uid = CardUid("AABBCCDD")

    private fun spool(
        api: FakeSpoolmanApi,
        id: Int,
        cardUids: List<CardUid> = emptyList(),
    ) {
        api.spoolList += SpoolmanSpool(
            id = id,
            filament = SpoolmanFilament(id = id, material = "PLA"),
            extra = if (cardUids.isEmpty()) null else mapOf(
                "card_uids" to ExtraCardUidsCodec.encode(cardUids),
            ),
        )
    }

    private suspend fun SpoolmanRepositoryHarness.enableNativeServer(
        version: String = "0.27.0",
    ) {
        fakeApi.info = SpoolmanInfo(version = version)
        repository.testConnection()
        fakeApi.callLog.clear()
    }

    @Test
    fun `findSpoolsByCardUid in BOTH unions native and legacy hits with native first`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        spool(h.fakeApi, 1, cardUids = listOf(uid))
        spool(h.fakeApi, 2)
        h.fakeApi.spoolTags["AABBCCDD"] = 2
        h.enableNativeServer()

        val outcome = h.repository.findSpoolsByCardUid(uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertEquals(
            listOf(2, 1),
            (outcome as SpoolmanOutcome.Success).data.map { it.id },
        )
    }

    @Test
    fun `findSpoolsByCardUid in BOTH dedupes spool present in both stores`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        spool(h.fakeApi, 1, cardUids = listOf(uid))
        h.fakeApi.spoolTags["AABBCCDD"] = 1
        h.enableNativeServer()

        val outcome = h.repository.findSpoolsByCardUid(uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertEquals(
            listOf(1),
            (outcome as SpoolmanOutcome.Success).data.map { it.id },
        )
    }

    @Test
    fun `findSpoolsByCardUid in NATIVE_ONLY queries only the tag store`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        spool(h.fakeApi, 1, cardUids = listOf(uid)) // stale legacy hit
        spool(h.fakeApi, 2)
        h.fakeApi.spoolTags["AABBCCDD"] = 2
        h.settingsRepository.setTagStorageMode(TagStorageMode.NATIVE_ONLY)
        h.enableNativeServer()

        val outcome = h.repository.findSpoolsByCardUid(uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertEquals(
            listOf(2),
            (outcome as SpoolmanOutcome.Success).data.map { it.id },
        )
        // Exactly one spool query — the tag-filtered one, no legacy bulk scan.
        assertEquals(
            listOf("listSpools(limit=1000,offset=0,allowArchived=true,tag=AABBCCDD)"),
            h.fakeApi.callLog.filter { it.startsWith("listSpools") },
        )
    }

    @Test
    fun `findSpoolsByCardUid in NATIVE_ONLY ignores stale legacy-only hits`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        spool(h.fakeApi, 1, cardUids = listOf(uid)) // legacy extra, no native tag
        // Probe under LEGACY_ONLY so the auto-mirror (which fires on the
        // info probe) doesn't link the stale legacy UID into the native
        // store before this test's dispatch behavior is exercised.
        h.settingsRepository.setTagStorageMode(TagStorageMode.LEGACY_ONLY)
        h.enableNativeServer()
        h.settingsRepository.setTagStorageMode(TagStorageMode.NATIVE_ONLY)

        val outcome = h.repository.findSpoolsByCardUid(uid)

        assertEquals(SpoolmanOutcome.Success(emptyList<SpoolmanSpool>()), outcome)
    }

    @Test
    fun `findSpoolsByCardUid on old server uses the legacy scan only`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        spool(h.fakeApi, 1, cardUids = listOf(uid))
        spool(h.fakeApi, 2)
        h.fakeApi.spoolTags["AABBCCDD"] = 2
        h.enableNativeServer(version = "0.1.0")

        val outcome = h.repository.findSpoolsByCardUid(uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertEquals(
            listOf(1),
            (outcome as SpoolmanOutcome.Success).data.map { it.id },
        )
        assertTrue(h.fakeApi.callLog.none { it.contains("tag=AABBCCDD") })
    }
}
