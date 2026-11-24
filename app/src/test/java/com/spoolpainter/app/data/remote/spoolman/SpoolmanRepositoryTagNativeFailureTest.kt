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
import java.io.IOException

/** Native-op failures after a successful legacy write (BOTH) must not fail
 *  the overall call; the same failures in NATIVE_ONLY are the result. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryTagNativeFailureTest {

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
    fun `native network failure in BOTH keeps legacy success`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1)
        h.enableNativeServer()
        h.fakeApi.failLinkTag = FakeSpoolmanApi.Failure.Throws(IOException("net down"))

        val outcome = h.repository.appendCardUidToSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertEquals(
            "\"AABBCCDD\"",
            (outcome as SpoolmanOutcome.Success).data.extra?.get("card_uids"),
        )
        // Legacy state landed; the native store was never touched.
        assertTrue(h.fakeApi.callLog.any { it.startsWith("patchSpool(1") })
        assertTrue(h.fakeApi.spoolTags.isEmpty())
    }

    @Test
    fun `native 5xx failure in BOTH keeps legacy success`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1)
        h.enableNativeServer()
        h.fakeApi.failLinkTag = FakeSpoolmanApi.Failure.Http(503, "maintenance")

        val outcome = h.repository.appendCardUidToSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertEquals(
            "\"AABBCCDD\"",
            (outcome as SpoolmanOutcome.Success).data.extra?.get("card_uids"),
        )
        assertTrue(h.fakeApi.spoolTags.isEmpty())
    }

    @Test
    fun `native network failure in NATIVE_ONLY fails the append`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1)
        h.settingsRepository.setTagStorageMode(TagStorageMode.NATIVE_ONLY)
        h.enableNativeServer()
        h.fakeApi.failLinkTag = FakeSpoolmanApi.Failure.Throws(IOException("net down"))

        val outcome = h.repository.appendCardUidToSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.NetworkError)
        assertTrue((outcome as SpoolmanOutcome.NetworkError).cause is IOException)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("patchSpool") })
        assertTrue(h.fakeApi.spoolTags.isEmpty())
    }

    @Test
    fun `native 5xx failure in NATIVE_ONLY fails the append`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1)
        h.settingsRepository.setTagStorageMode(TagStorageMode.NATIVE_ONLY)
        h.enableNativeServer()
        h.fakeApi.failLinkTag = FakeSpoolmanApi.Failure.Http(500, "boom")

        val outcome = h.repository.appendCardUidToSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.HttpError)
        assertEquals(500, (outcome as SpoolmanOutcome.HttpError).code)
        assertTrue(h.fakeApi.spoolTags.isEmpty())
    }

    @Test
    fun `native unlink network failure in BOTH keeps legacy removal success`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1, cardUids = listOf(uid))
        h.fakeApi.spoolTags["AABBCCDD"] = 1
        h.enableNativeServer()
        h.fakeApi.failUnlinkTag = FakeSpoolmanApi.Failure.Throws(IOException("net down"))

        val outcome = h.repository.removeCardUidFromSpool(1, uid)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        // Legacy removal landed; the tag store keeps its (now stale) entry.
        val spool = h.fakeApi.spoolList.first { it.id == 1 }
        assertEquals("\"\"", spool.extra?.get("card_uids"))
        assertEquals(1, h.fakeApi.spoolTags["AABBCCDD"])
    }
}
