package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.data.local.TagStorageMode
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanInfo
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Per-mode dispatch of [SpoolmanRepository.appendCardUidToSpool]: BOTH /
 *  NATIVE_ONLY / LEGACY_ONLY on capable servers, and legacy fallback on
 *  old (or unprobed) servers. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryTagModeDispatchTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val uid = CardUid("AABBCCDD")

    private fun seedSpool(
        api: FakeSpoolmanApi,
        id: Int,
        extra: Map<String, String>? = null,
    ): SpoolmanSpool {
        val spool = SpoolmanSpool(
            id = id,
            filament = SpoolmanFilament(id = id, material = "PLA"),
            extra = extra,
        )
        api.spoolList += spool
        return spool
    }

    /** Points the fake at a 0.27.0-grade server and refreshes serverInfo
     *  via testConnection (the only info-probe path); clears the call log
     *  so assertions cover just the operation under test. */
    private suspend fun SpoolmanRepositoryHarness.enableNativeServer(
        version: String = "0.27.0",
    ) {
        fakeApi.info = SpoolmanInfo(version = version)
        repository.testConnection()
        fakeApi.callLog.clear()
    }

    @Test
    fun `append in BOTH patches extra and links native tag`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1)
        h.enableNativeServer()

        val outcome = h.repository.appendCardUidToSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        val spool = (outcome as SpoolmanOutcome.Success).data
        assertEquals("\"AABBCCDD\"", spool.extra?.get("card_uids"))
        assertEquals(1, h.fakeApi.spoolTags["AABBCCDD"])
        assertEquals(TagLinkBody(uid = "AABBCCDD"), h.fakeApi.lastLinkTagBody)
        // Legacy write happens before the native link.
        val patchIdx = h.fakeApi.callLog.indexOfFirst { it.startsWith("patchSpool(1") }
        val linkIdx = h.fakeApi.callLog.indexOfFirst { it.startsWith("linkTag(1") }
        assertTrue(patchIdx in h.fakeApi.callLog.indices)
        assertTrue(linkIdx in h.fakeApi.callLog.indices)
        assertTrue(patchIdx < linkIdx)
    }

    @Test
    fun `append in NATIVE_ONLY writes only the tag store`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1)
        h.settingsRepository.setTagStorageMode(TagStorageMode.NATIVE_ONLY)
        h.enableNativeServer()

        val outcome = h.repository.appendCardUidToSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        val spool = (outcome as SpoolmanOutcome.Success).data
        assertEquals(1, spool.id)
        assertNull(spool.extra)
        assertEquals(1, h.fakeApi.spoolTags["AABBCCDD"])
        assertTrue(h.fakeApi.callLog.none { it.startsWith("patchSpool") })
        // No extra-field registration either — card_uids is never needed.
        assertTrue(h.fakeApi.callLog.none { it.startsWith("listFields") })
        assertTrue(h.fakeApi.callLog.none { it.startsWith("postField") })
    }

    @Test
    fun `append in LEGACY_ONLY ignores native store on 0_27 server`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1)
        h.settingsRepository.setTagStorageMode(TagStorageMode.LEGACY_ONLY)
        h.enableNativeServer()

        val outcome = h.repository.appendCardUidToSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertEquals(
            "\"AABBCCDD\"",
            (outcome as SpoolmanOutcome.Success).data.extra?.get("card_uids"),
        )
        assertTrue(h.fakeApi.callLog.none { it.contains("linkTag") })
        assertTrue(h.fakeApi.spoolTags.isEmpty())
    }

    @Test
    fun `append in BOTH falls back to legacy on 0_1_0 server`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1)
        h.enableNativeServer(version = "0.1.0")

        val outcome = h.repository.appendCardUidToSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertEquals(
            "\"AABBCCDD\"",
            (outcome as SpoolmanOutcome.Success).data.extra?.get("card_uids"),
        )
        assertTrue(h.fakeApi.callLog.none { it.contains("linkTag") })
        assertTrue(h.fakeApi.spoolTags.isEmpty())
    }

    @Test
    fun `append in BOTH falls back to legacy when server version is unprobed`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1)
        // No testConnection → serverInfo stays null → fail-closed to legacy.

        val outcome = h.repository.appendCardUidToSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertEquals(
            "\"AABBCCDD\"",
            (outcome as SpoolmanOutcome.Success).data.extra?.get("card_uids"),
        )
        assertTrue(h.fakeApi.callLog.none { it.contains("linkTag") })
    }
}
