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
class SpoolmanRepositoryRemoveCardUidTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun seedSpool(api: FakeSpoolmanApi, id: Int, lotNr: String?) {
        api.spoolList += SpoolmanSpool(
            id = id,
            filament = SpoolmanFilament(id = id, material = "PLA"),
            lot_nr = lotNr,
        )
    }

    @Test
    fun `UID removed when present`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, "card_uid:abcd,card_uid:ffee")
        val outcome = h.repository.removeCardUidFromSpool(1, CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals("card_uid:ffee", (outcome as SpoolmanOutcome.Success).data.lot_nr)
    }

    @Test
    fun `UID absent is no-op idempotent`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, "card_uid:ffee")
        val outcome = h.repository.removeCardUidFromSpool(1, CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals("card_uid:ffee", (outcome as SpoolmanOutcome.Success).data.lot_nr)
    }

    @Test
    fun `opaque tail preserved on remove`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, "card_uid:abcd,batch-42")
        val outcome = h.repository.removeCardUidFromSpool(1, CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals("batch-42", (outcome as SpoolmanOutcome.Success).data.lot_nr)
    }

    @Test
    fun `removing last UID with no opaque clears lot_nr to empty string`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, "card_uid:abcd")
        val outcome = h.repository.removeCardUidFromSpool(1, CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals("", (outcome as SpoolmanOutcome.Success).data.lot_nr)
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
        seedSpool(h.fakeApi, 1, "card_uid:abcd")
        h.fakeApi.failGetSpool = FakeSpoolmanApi.Failure.Http(404, "missing")
        val outcome = h.repository.removeCardUidFromSpool(1, CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.HttpError)
    }

    @Test
    fun `IOException on PATCH returns NetworkError`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seedSpool(h.fakeApi, 1, "card_uid:abcd")
        h.fakeApi.failPatchSpoolLotNr = FakeSpoolmanApi.Failure.Throws(IOException("eof"))
        val outcome = h.repository.removeCardUidFromSpool(1, CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
    }
}
