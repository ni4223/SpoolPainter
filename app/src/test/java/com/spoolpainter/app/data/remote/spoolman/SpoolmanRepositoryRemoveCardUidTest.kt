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
class SpoolmanRepositoryRemoveCardUidTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun seedSpool(api: FakeSpoolmanApi, id: Int, extra: Map<String, String>? = null) {
        api.spoolList += SpoolmanSpool(
            id = id,
            filament = SpoolmanFilament(id = id, material = "PLA"),
            extra = extra,
        )
    }

    @Test
    fun `removeCardUidFromSpool happyPath removesUidFromList`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(
            h.fakeApi, 1,
            extra = mapOf(
                "card_uids" to ExtraCardUidsCodec.encode(
                    listOf(CardUid("AABBCCDD"), CardUid("11223344")),
                ),
            ),
        )
        val outcome = h.repository.removeCardUidFromSpool(1, CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals(
            "\"11223344\"",
            (outcome as SpoolmanOutcome.Success).data.extra?.get("card_uids"),
        )
    }

    @Test
    fun `removeCardUidFromSpool emptyResult preservesCardUidsKey`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(
            h.fakeApi, 1,
            extra = mapOf("card_uids" to ExtraCardUidsCodec.encode(listOf(CardUid("AABBCCDD")))),
        )
        val outcome = h.repository.removeCardUidFromSpool(1, CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals("\"\"", (outcome as SpoolmanOutcome.Success).data.extra?.get("card_uids"))
    }

    @Test
    fun `removeCardUidFromSpool idempotent whenUidAbsent`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(
            h.fakeApi, 1,
            extra = mapOf("card_uids" to ExtraCardUidsCodec.encode(listOf(CardUid("11223344")))),
        )
        val outcome = h.repository.removeCardUidFromSpool(1, CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("patchSpool") })
    }

    @Test
    fun `removeCardUidFromSpool lazyBootstrap on400UnknownField`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        // Field NOT pre-registered → first PATCH 400, bootstrap registers, retry succeeds.
        seedSpool(
            h.fakeApi, 1,
            extra = mapOf("card_uids" to ExtraCardUidsCodec.encode(listOf(CardUid("AABBCCDD")))),
        )
        val outcome = h.repository.removeCardUidFromSpool(1, CardUid("AABBCCDD"))
        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.contains("postField(spool,card_uids)"))
    }

    @Test
    fun `empty UID rejected`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        val outcome = h.repository.removeCardUidFromSpool(1, CardUid(""))
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        assertTrue((outcome as SpoolmanOutcome.NetworkError).cause is IllegalArgumentException)
    }

    @Test
    fun `HttpError on read propagates`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(
            h.fakeApi, 1,
            extra = mapOf("card_uids" to ExtraCardUidsCodec.encode(listOf(CardUid("AABBCCDD")))),
        )
        h.fakeApi.failGetSpool = FakeSpoolmanApi.Failure.Http(404, "missing")
        val outcome = h.repository.removeCardUidFromSpool(1, CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.HttpError)
    }

    @Test
    fun `IOException on PATCH returns NetworkError`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        seedSpool(
            h.fakeApi, 1,
            extra = mapOf("card_uids" to ExtraCardUidsCodec.encode(listOf(CardUid("AABBCCDD")))),
        )
        h.fakeApi.failPatchSpool = FakeSpoolmanApi.Failure.Throws(IOException("eof"))
        val outcome = h.repository.removeCardUidFromSpool(1, CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
    }
}
