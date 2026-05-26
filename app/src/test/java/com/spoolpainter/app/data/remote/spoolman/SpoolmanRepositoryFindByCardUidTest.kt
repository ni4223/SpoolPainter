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
class SpoolmanRepositoryFindByCardUidTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun spool(
        id: Int,
        cardUids: List<CardUid> = emptyList(),
        archived: Boolean = false,
        extraOverride: Map<String, String>? = null,
    ): SpoolmanSpool = SpoolmanSpool(
        id = id,
        filament = SpoolmanFilament(id = id, name = null, material = "PLA"),
        archived = archived,
        extra = extraOverride ?: if (cardUids.isEmpty()) null else mapOf(
            "card_uids" to ExtraCardUidsCodec.encode(cardUids),
        ),
    )

    @Test
    fun `empty CardUid short-circuits to empty Success without HTTP`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        val outcome = h.repository.findSpoolsByCardUid(CardUid(""))
        assertEquals(SpoolmanOutcome.Success(emptyList<SpoolmanSpool>()), outcome)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("listSpools") })
    }

    @Test
    fun `findSpoolsByCardUid returnsMatch byDecodedExtraCardUids`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolList += spool(1, cardUids = listOf(CardUid("AABBCCDD")))
        h.fakeApi.spoolList += spool(2, cardUids = listOf(CardUid("11223344")))
        val outcome = h.repository.findSpoolsByCardUid(CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals(listOf(1), (outcome as SpoolmanOutcome.Success).data.map { it.id })
    }

    @Test
    fun `findSpoolsByCardUid includesArchivedSpools`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolList += spool(1, cardUids = listOf(CardUid("AABBCCDD")), archived = true)
        val outcome = h.repository.findSpoolsByCardUid(CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals(listOf(1), (outcome as SpoolmanOutcome.Success).data.map { it.id })
        assertTrue(h.fakeApi.callLog.any { it.contains("allowArchived=true") })
    }

    @Test
    fun `findSpoolsByCardUid returnsEmpty whenNoMatch`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolList += spool(1, cardUids = listOf(CardUid("11223344")))
        val outcome = h.repository.findSpoolsByCardUid(CardUid("AABBCCDD"))
        assertEquals(SpoolmanOutcome.Success(emptyList<SpoolmanSpool>()), outcome)
    }

    @Test
    fun `findSpoolsByCardUid normalisesUidCaseAtCompareTime`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolList += spool(1, extraOverride = mapOf("card_uids" to "\"aabbccdd\""))
        val outcome = h.repository.findSpoolsByCardUid(CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals(listOf(1), (outcome as SpoolmanOutcome.Success).data.map { it.id })
    }

    @Test
    fun `query uses limit 1000 offset 0 allowArchived true`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.repository.findSpoolsByCardUid(CardUid("AABBCCDD"))
        assertTrue(h.fakeApi.callLog.contains("listSpools(limit=1000,offset=0,allowArchived=true)"))
    }

    @Test
    fun `HttpError surfaces with code and connectivity Reachable`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.failListSpools = FakeSpoolmanApi.Failure.Http(500, "boom")
        val outcome = h.repository.findSpoolsByCardUid(CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertEquals(500, (outcome as SpoolmanOutcome.HttpError).code)
        assertEquals(ConnectivityState.Reachable, h.repository.connectivity.value)
    }

    @Test
    fun `IOException returns NetworkError and connectivity Unreachable`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.failListSpools = FakeSpoolmanApi.Failure.Throws(IOException("eof"))
        val outcome = h.repository.findSpoolsByCardUid(CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        assertTrue(h.repository.connectivity.value is ConnectivityState.Unreachable)
    }

    @Test
    fun `URL not configured short-circuits and connectivity stays Unknown`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder, initialUrl = "")
        val outcome = h.repository.findSpoolsByCardUid(CardUid("AABBCCDD"))
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        assertTrue((outcome as SpoolmanOutcome.NetworkError).cause is UrlNotConfiguredException)
        assertEquals(ConnectivityState.Unknown, h.repository.connectivity.value)
    }
}
