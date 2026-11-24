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

/** Per-mode dispatch of [SpoolmanRepository.removeCardUidFromSpool],
 *  including the idempotent 404 of the native unlink (real Spoolman 0.27.0
 *  phrasing: "Spool N has no tag with UID X.") and the unknown-spool 404
 *  error. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryRemoveTagModeTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val uid = CardUid("AABBCCDD")

    private fun seedSpool(
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

    private suspend fun SpoolmanRepositoryHarness.enableNativeServer() {
        fakeApi.info = SpoolmanInfo(version = "0.27.0")
        repository.testConnection()
        fakeApi.callLog.clear()
    }

    @Test
    fun `remove in BOTH clears extra and unlinks native tag`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1, cardUids = listOf(uid))
        h.fakeApi.spoolTags["AABBCCDD"] = 1
        h.enableNativeServer()

        val outcome = h.repository.removeCardUidFromSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        val spool = h.fakeApi.spoolList.first { it.id == 1 }
        assertEquals("\"\"", spool.extra?.get("card_uids"))
        assertNull(h.fakeApi.spoolTags["AABBCCDD"])
        assertTrue(h.fakeApi.callLog.any { it.startsWith("unlinkTag(1,uid=AABBCCDD)") })
    }

    @Test
    fun `remove in NATIVE_ONLY unlinks tag and leaves legacy extra untouched`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, cardUids = listOf(uid))
        h.fakeApi.spoolTags["AABBCCDD"] = 1
        h.settingsRepository.setTagStorageMode(TagStorageMode.NATIVE_ONLY)
        h.enableNativeServer()

        val outcome = h.repository.removeCardUidFromSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertNull(h.fakeApi.spoolTags["AABBCCDD"])
        // No extra-field writes in NATIVE_ONLY — the stale legacy value stays.
        val spool = h.fakeApi.spoolList.first { it.id == 1 }
        assertEquals("\"AABBCCDD\"", spool.extra?.get("card_uids"))
        assertTrue(h.fakeApi.callLog.none { it.startsWith("patchSpool") })
        // The overall result is the freshly-fetched spool.
        assertEquals(1, (outcome as SpoolmanOutcome.Success).data.id)
    }

    @Test
    fun `remove in LEGACY_ONLY ignores native store on 0_27 server`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1, cardUids = listOf(uid))
        h.fakeApi.spoolTags["AABBCCDD"] = 1
        h.settingsRepository.setTagStorageMode(TagStorageMode.LEGACY_ONLY)
        h.enableNativeServer()

        val outcome = h.repository.removeCardUidFromSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        val spool = h.fakeApi.spoolList.first { it.id == 1 }
        assertEquals("\"\"", spool.extra?.get("card_uids"))
        // The tag store was not consulted.
        assertEquals(1, h.fakeApi.spoolTags["AABBCCDD"])
        assertTrue(h.fakeApi.callLog.none { it.contains("unlinkTag") })
    }

    @Test
    fun `remove in BOTH with UID only in legacy extra succeeds and clears extra`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1, cardUids = listOf(uid))
        // Native store empty (standard rollout state: UID only ever lived
        // in extra.card_uids) → unlink answers the real 0.27.0 404 "Spool 1
        // has no tag with UID AABBCCDD.", which must not fail the overall
        // (legacy) removal. Probe under LEGACY_ONLY so the auto-mirror
        // doesn't link the UID before the removal runs.
        h.settingsRepository.setTagStorageMode(TagStorageMode.LEGACY_ONLY)
        h.enableNativeServer()
        h.settingsRepository.setTagStorageMode(TagStorageMode.BOTH)

        val outcome = h.repository.removeCardUidFromSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        val spool = h.fakeApi.spoolList.first { it.id == 1 }
        assertTrue(
            "extra still contains uid: ${spool.extra?.get("card_uids")}",
            ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "").none { it == uid },
        )
        assertTrue(h.fakeApi.callLog.any { it.startsWith("unlinkTag(1,uid=AABBCCDD)") })
    }

    @Test
    fun `remove in NATIVE_ONLY treats 404 has-no-tag as idempotent success`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, cardUids = listOf(uid))
        // Probe under LEGACY_ONLY so the auto-mirror doesn't link the
        // legacy UID first — the removal must hit the empty tag store.
        h.settingsRepository.setTagStorageMode(TagStorageMode.LEGACY_ONLY)
        h.enableNativeServer()
        h.settingsRepository.setTagStorageMode(TagStorageMode.NATIVE_ONLY)

        val outcome = h.repository.removeCardUidFromSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertNull(h.fakeApi.spoolTags["AABBCCDD"])
        assertTrue(h.fakeApi.callLog.none { it.startsWith("patchSpool") })
    }

    @Test
    fun `remove in BOTH treats any unmatched native 404 as benign`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1, cardUids = listOf(uid))
        h.enableNativeServer()
        // A 404 whose wording matches none of the idempotent phrasings:
        // after a successful legacy removal it is still benign on the
        // unlink path — the UID is not natively linked either way.
        h.fakeApi.failUnlinkTag = FakeSpoolmanApi.Failure.Http(404, "no such spool")

        val outcome = h.repository.removeCardUidFromSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        val spool = h.fakeApi.spoolList.first { it.id == 1 }
        assertEquals("\"\"", spool.extra?.get("card_uids"))
    }

    @Test
    fun `remove in NATIVE_ONLY still accepts no-such-tag 404 wording`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1)
        h.settingsRepository.setTagStorageMode(TagStorageMode.NATIVE_ONLY)
        h.enableNativeServer()
        h.fakeApi.failUnlinkTag = FakeSpoolmanApi.Failure.Http(404, "no such tag")

        val outcome = h.repository.removeCardUidFromSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
    }

    @Test
    fun `remove in NATIVE_ONLY still accepts tag-not-found 404 wording`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1)
        h.settingsRepository.setTagStorageMode(TagStorageMode.NATIVE_ONLY)
        h.enableNativeServer()
        h.fakeApi.failUnlinkTag = FakeSpoolmanApi.Failure.Http(404, "tag not found")

        val outcome = h.repository.removeCardUidFromSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
    }

    @Test
    fun `remove in NATIVE_ONLY returns error for unknown spool`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.settingsRepository.setTagStorageMode(TagStorageMode.NATIVE_ONLY)
        h.enableNativeServer()

        val outcome = h.repository.removeCardUidFromSpool(99, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.HttpError)
        assertEquals(404, (outcome as SpoolmanOutcome.HttpError).code)
    }

    @Test
    fun `append in NATIVE_ONLY returns error for unknown spool`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.settingsRepository.setTagStorageMode(TagStorageMode.NATIVE_ONLY)
        h.enableNativeServer()

        val outcome = h.repository.appendCardUidToSpool(99, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.HttpError)
        assertEquals(404, (outcome as SpoolmanOutcome.HttpError).code)
    }
}
