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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Append move-on-409: a UID held by another spool is DELETEd from the
 *  holder and POSTed to the target. BOTH additionally mirrors the removal
 *  into the holder's legacy `extra.card_uids`; NATIVE_ONLY leaves legacy
 *  state untouched. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryTagMoveTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val uid = CardUid("AABBCCDD")

    private fun seedSpool(
        api: FakeSpoolmanApi,
        id: Int,
        cardUids: List<CardUid> = emptyList(),
    ): SpoolmanSpool {
        val extra = if (cardUids.isEmpty()) null else mapOf(
            "card_uids" to ExtraCardUidsCodec.encode(cardUids),
        )
        val spool = SpoolmanSpool(
            id = id,
            filament = SpoolmanFilament(id = id, material = "PLA"),
            extra = extra,
        )
        api.spoolList += spool
        return spool
    }

    private suspend fun SpoolmanRepositoryHarness.enableNativeServer() {
        fakeApi.info = SpoolmanInfo(version = "0.27.0")
        repository.testConnection()
        fakeApi.callLog.clear()
    }

    @Test
    fun `append move-on-409 in BOTH frees holder in tag store and legacy extra`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1, cardUids = listOf(uid))
        seedSpool(h.fakeApi, 2)
        h.fakeApi.spoolTags["AABBCCDD"] = 1
        h.enableNativeServer()

        val outcome = h.repository.appendCardUidToSpool(2, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        // Target gained the UID in both stores.
        assertEquals(2, h.fakeApi.spoolTags["AABBCCDD"])
        val target = h.fakeApi.spoolList.first { it.id == 2 }
        assertEquals("\"AABBCCDD\"", target.extra?.get("card_uids"))
        // Holder lost the UID in both stores.
        val holder = h.fakeApi.spoolList.first { it.id == 1 }
        assertEquals("\"\"", holder.extra?.get("card_uids"))
        // Move sequence: unlink from holder, then re-link on target (the
        // first linkTag(2) attempt is the 409 that triggered the move).
        val unlinkIdx = h.fakeApi.callLog.indexOfFirst { it.startsWith("unlinkTag(1") }
        val relinkIdx = h.fakeApi.callLog.indexOfLast { it.startsWith("linkTag(2") }
        assertTrue(unlinkIdx in h.fakeApi.callLog.indices)
        assertTrue(relinkIdx in h.fakeApi.callLog.indices)
        assertTrue(unlinkIdx < relinkIdx)
    }

    @Test
    fun `append move-on-409 in BOTH returns target spool as overall result`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1, cardUids = listOf(uid))
        seedSpool(h.fakeApi, 2)
        h.fakeApi.spoolTags["AABBCCDD"] = 1
        h.enableNativeServer()

        val outcome = h.repository.appendCardUidToSpool(2, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertEquals(2, (outcome as SpoolmanOutcome.Success).data.id)
        assertEquals("\"AABBCCDD\"", outcome.data.extra?.get("card_uids"))
    }

    @Test
    fun `append move-on-409 in NATIVE_ONLY moves tag without legacy writes`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, cardUids = listOf(uid))
        seedSpool(h.fakeApi, 2)
        h.fakeApi.spoolTags["AABBCCDD"] = 1
        h.settingsRepository.setTagStorageMode(TagStorageMode.NATIVE_ONLY)
        h.enableNativeServer()

        val outcome = h.repository.appendCardUidToSpool(2, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertEquals(2, h.fakeApi.spoolTags["AABBCCDD"])
        // No legacy mirror: the holder's stale extra is deliberately left
        // as-is in NATIVE_ONLY.
        val holder = h.fakeApi.spoolList.first { it.id == 1 }
        assertEquals("\"AABBCCDD\"", holder.extra?.get("card_uids"))
        val target = h.fakeApi.spoolList.first { it.id == 2 }
        assertNull(target.extra?.get("card_uids"))
        assertTrue(h.fakeApi.callLog.none { it.startsWith("patchSpool") })
        // The overall result is the freshly-fetched target spool.
        assertEquals(2, (outcome as SpoolmanOutcome.Success).data.id)
    }
}
