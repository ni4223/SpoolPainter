package com.spoolpainter.app.data.remote.spoolman

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryEnsureExtraFieldsTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `bothFieldsAlreadyRegistered emitsZeroPosts`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        h.fakeApi.filamentExtraFields += "variant"
        val outcome = h.repository.ensureExtraFieldsRegistered()
        assertEquals(SpoolmanOutcome.Success(Unit), outcome)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("postField") })
    }

    @Test
    fun `cardUidsMissing emitsOnePostForSpool`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.filamentExtraFields += "variant"
        val outcome = h.repository.ensureExtraFieldsRegistered()
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals(
            1,
            h.fakeApi.callLog.count { it == "postField(spool,card_uids)" },
        )
        assertEquals(0, h.fakeApi.callLog.count { it.startsWith("postField(filament") })
    }

    @Test
    fun `variantMissing emitsOnePostForFilament`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        val outcome = h.repository.ensureExtraFieldsRegistered()
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals(
            1,
            h.fakeApi.callLog.count { it == "postField(filament,variant)" },
        )
        assertEquals(0, h.fakeApi.callLog.count { it.startsWith("postField(spool") })
    }

    @Test
    fun `bothMissing emitsTwoPostsInOrderSpoolFirst`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        val outcome = h.repository.ensureExtraFieldsRegistered()
        assertTrue(outcome is SpoolmanOutcome.Success)
        val postEntries = h.fakeApi.callLog.filter { it.startsWith("postField") }
        assertEquals(
            listOf("postField(spool,card_uids)", "postField(filament,variant)"),
            postEntries,
        )
    }
}
