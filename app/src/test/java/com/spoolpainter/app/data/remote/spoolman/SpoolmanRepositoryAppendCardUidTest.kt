package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid
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

    private fun seedSpool(api: FakeSpoolmanApi, id: Int, lotNr: String?): SpoolmanSpool {
        val spool = SpoolmanSpool(
            id = id,
            filament = SpoolmanFilament(id = id, material = "PLA"),
            lot_nr = lotNr,
        )
        api.spoolList += spool
        return spool
    }

    @Test
    fun `UID appended when absent`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, null)
        val outcome = h.repository.appendCardUidToSpool(1, CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals("card_uid:abcd", (outcome as SpoolmanOutcome.Success).data.lot_nr)
    }

    @Test
    fun `UID idempotent when already present`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, "card_uid:abcd")
        val outcome = h.repository.appendCardUidToSpool(1, CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals("card_uid:abcd", (outcome as SpoolmanOutcome.Success).data.lot_nr)
    }

    @Test
    fun `opaque tail preserved on append`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, "card_uid:ffee,batch-42")
        val outcome = h.repository.appendCardUidToSpool(1, CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals("card_uid:ffee,card_uid:abcd,batch-42", (outcome as SpoolmanOutcome.Success).data.lot_nr)
    }

    @Test
    fun `empty UID rejected with NetworkError IllegalArgumentException`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        val outcome = h.repository.appendCardUidToSpool(1, CardUid(""))
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        assertTrue((outcome as SpoolmanOutcome.NetworkError).cause is IllegalArgumentException)
    }

    @Test
    fun `HttpError on read step propagates`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, null)
        h.fakeApi.failGetSpool = FakeSpoolmanApi.Failure.Http(500, "boom")
        val outcome = h.repository.appendCardUidToSpool(1, CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertEquals(500, (outcome as SpoolmanOutcome.HttpError).code)
    }

    @Test
    fun `IOException on read step returns NetworkError`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, null)
        h.fakeApi.failGetSpool = FakeSpoolmanApi.Failure.Throws(IOException("eof"))
        val outcome = h.repository.appendCardUidToSpool(1, CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
    }

    @Test
    fun `IOException on PATCH step returns NetworkError after successful read`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, null)
        h.fakeApi.failPatchSpoolLotNr = FakeSpoolmanApi.Failure.Throws(IOException("conn"))
        val outcome = h.repository.appendCardUidToSpool(1, CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
    }
}
