package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryRefreshTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `refresh full success populates all three caches`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.vendorList += SpoolmanVendor(id = 1, name = "V1")
        h.fakeApi.filamentList += SpoolmanFilament(id = 11, material = "PLA")
        h.fakeApi.spoolList += SpoolmanSpool(
            id = 21,
            filament = SpoolmanFilament(id = 11, material = "PLA"),
        )
        val outcome = h.repository.refresh()
        assertEquals(SpoolmanOutcome.Success(Unit), outcome)
        assertEquals(1, h.repository.vendors.value.size)
        assertEquals(1, h.repository.filaments.value.size)
        assertEquals(1, h.repository.spools.value.size)
    }

    @Test
    fun `fail at vendors aborts refresh and leaves caches untouched`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.failListVendors = FakeSpoolmanApi.Failure.Http(500, "boom")
        val outcome = h.repository.refresh()
        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertTrue(h.repository.vendors.value.isEmpty())
        assertTrue(h.repository.filaments.value.isEmpty())
        assertTrue(h.repository.spools.value.isEmpty())
        assertTrue(h.fakeApi.callLog.none { it == "listFilaments" || it.startsWith("listSpools") })
    }

    @Test
    fun `fail at filaments aborts after vendors fetched`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.vendorList += SpoolmanVendor(id = 1, name = "V1")
        h.fakeApi.failListFilaments = FakeSpoolmanApi.Failure.Http(500, "boom")
        val outcome = h.repository.refresh()
        assertTrue(outcome is SpoolmanOutcome.HttpError)
        // Caches remain empty per atomic update rule (BR-U3-REFRESH-3).
        assertTrue(h.repository.vendors.value.isEmpty())
    }

    @Test
    fun `fail at spools aborts after vendors and filaments fetched`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.vendorList += SpoolmanVendor(id = 1, name = "V1")
        h.fakeApi.filamentList += SpoolmanFilament(id = 11, material = "PLA")
        h.fakeApi.failListSpools = FakeSpoolmanApi.Failure.Http(500, "boom")
        val outcome = h.repository.refresh()
        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertTrue(h.repository.spools.value.isEmpty())
    }
}
