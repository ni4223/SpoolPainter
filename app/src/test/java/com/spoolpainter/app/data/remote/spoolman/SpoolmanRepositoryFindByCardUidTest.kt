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
class SpoolmanRepositoryFindByCardUidTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun spool(id: Int, lotNr: String?): SpoolmanSpool =
        SpoolmanSpool(
            id = id,
            filament = SpoolmanFilament(id = id, name = null, material = "PLA"),
            lot_nr = lotNr,
        )

    @Test
    fun `empty CardUid short-circuits to empty Success without HTTP`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        val outcome = h.repository.findSpoolsByCardUid(CardUid(""))
        assertEquals(SpoolmanOutcome.Success(emptyList<SpoolmanSpool>()), outcome)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("findSpoolsByLotNr") })
    }

    @Test
    fun `single match returns one spool`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolList += spool(1, "card_uid:abcd")
        h.fakeApi.spoolList += spool(2, "card_uid:ffee")
        val outcome = h.repository.findSpoolsByCardUid(CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals(listOf(1), (outcome as SpoolmanOutcome.Success).data.map { it.id })
    }

    @Test
    fun `multi match returns all matching spools (no disambiguation)`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolList += spool(1, "card_uid:abcd")
        h.fakeApi.spoolList += spool(2, "card_uid:abcd,card_uid:ffee")
        val outcome = h.repository.findSpoolsByCardUid(CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals(listOf(1, 2), (outcome as SpoolmanOutcome.Success).data.map { it.id })
    }

    @Test
    fun `no match returns empty Success`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolList += spool(1, "card_uid:ffee")
        val outcome = h.repository.findSpoolsByCardUid(CardUid("abcd"))
        assertEquals(SpoolmanOutcome.Success(emptyList<SpoolmanSpool>()), outcome)
    }

    @Test
    fun `query string includes card_uid prefix`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.repository.findSpoolsByCardUid(CardUid("abcd"))
        assertTrue(h.fakeApi.callLog.contains("findSpoolsByLotNr(card_uid:abcd)"))
    }

    @Test
    fun `HttpError surfaces with code and connectivity Reachable`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.failFindSpoolsByLotNr = FakeSpoolmanApi.Failure.Http(500, "boom")
        val outcome = h.repository.findSpoolsByCardUid(CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertEquals(500, (outcome as SpoolmanOutcome.HttpError).code)
        assertEquals(ConnectivityState.Reachable, h.repository.connectivity.value)
    }

    @Test
    fun `IOException returns NetworkError and connectivity Unreachable`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.failFindSpoolsByLotNr = FakeSpoolmanApi.Failure.Throws(IOException("eof"))
        val outcome = h.repository.findSpoolsByCardUid(CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        assertTrue(h.repository.connectivity.value is ConnectivityState.Unreachable)
    }

    @Test
    fun `URL not configured short-circuits and connectivity stays Unknown`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder, initialUrl = "")
        val outcome = h.repository.findSpoolsByCardUid(CardUid("abcd"))
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        assertTrue((outcome as SpoolmanOutcome.NetworkError).cause is UrlNotConfiguredException)
        assertEquals(ConnectivityState.Unknown, h.repository.connectivity.value)
    }
}
