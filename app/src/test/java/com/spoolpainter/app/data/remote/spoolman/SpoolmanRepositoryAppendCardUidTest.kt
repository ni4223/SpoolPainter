package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
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

@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryAppendCardUidTest {

    @get:Rule val tempFolder = TemporaryFolder()

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

    @Test
    fun `appendCardUidToSpool emitsFullExtraPatch`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1)
        val outcome = h.repository.appendCardUidToSpool(1, CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        val spool = (outcome as SpoolmanOutcome.Success).data
        assertEquals("\"AABBCCDD\"", spool.extra?.get("card_uids"))
        assertTrue(h.fakeApi.callLog.any { it.startsWith("patchSpool(1,extra={card_uids=\"AABBCCDD\"") })
    }

    @Test
    fun `appendCardUidToSpool idempotent whenUidAlreadyPresent`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(
            h.fakeApi, 1,
            extra = mapOf("card_uids" to ExtraCardUidsCodec.encode(listOf(CardUid("AABBCCDD")))),
        )
        val outcome = h.repository.appendCardUidToSpool(1, CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("patchSpool") })
    }

    @Test
    fun `appendCardUidToSpool preservesOtherExtraKeys`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        h.fakeApi.spoolExtraFields += "foo"
        seedSpool(h.fakeApi, 1, extra = mapOf("foo" to "bar"))
        val outcome = h.repository.appendCardUidToSpool(1, CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        val spool = (outcome as SpoolmanOutcome.Success).data
        assertEquals("bar", spool.extra?.get("foo"))
        assertEquals("\"AABBCCDD\"", spool.extra?.get("card_uids"))
    }

    @Test
    fun `appendCardUidToSpool lazyBootstrap on400UnknownField`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        // Field NOT pre-registered → first PATCH 400, bootstrap registers, retry succeeds.
        seedSpool(h.fakeApi, 1)
        val outcome = h.repository.appendCardUidToSpool(1, CardUid("AABBCCDD"))
        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.contains("postField(spool,card_uids)"))
        // Two patchSpool entries: the failing first attempt + the retry.
        assertEquals(
            2,
            h.fakeApi.callLog.count { it.startsWith("patchSpool(1") },
        )
    }

    @Test
    fun `appendCardUidToSpool propagates400AfterBootstrap`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1)
        // Sticky 400 ensures both attempts fail; bootstrap runs in between but
        // cannot help. The second 400 is what the helper surfaces.
        h.fakeApi.failPatchSpool = FakeSpoolmanApi.Failure.Http(
            400, "Unknown extra field: card_uids",
        )
        val outcome = h.repository.appendCardUidToSpool(1, CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertEquals(400, (outcome as SpoolmanOutcome.HttpError).code)
        // Two attempts means two patchSpool log entries.
        assertEquals(2, h.fakeApi.callLog.count { it.startsWith("patchSpool(1") })
    }

    @Test
    fun `appendCardUidToSpool returnsErrorOnGetFailure`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1)
        h.fakeApi.failGetSpool = FakeSpoolmanApi.Failure.Http(500, "boom")
        val outcome = h.repository.appendCardUidToSpool(1, CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertEquals(500, (outcome as SpoolmanOutcome.HttpError).code)
    }

    @Test
    fun `empty UID rejected with NetworkError IllegalArgumentException`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        val outcome = h.repository.appendCardUidToSpool(1, CardUid(""))
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        assertTrue((outcome as SpoolmanOutcome.NetworkError).cause is IllegalArgumentException)
    }

    @Test
    fun `IOException on PATCH step returns NetworkError after successful read`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(h.fakeApi, 1)
        h.fakeApi.failPatchSpool = FakeSpoolmanApi.Failure.Throws(IOException("conn"))
        val outcome = h.repository.appendCardUidToSpool(1, CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
    }
}
