package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.ExternalFilament
import com.spoolpainter.app.domain.models.SpoolmanInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * SpoolmanDB search — GET /api/v1/external/filament/search via
 * SpoolmanRepository.searchExternalFilaments. totalCount comes from the
 * x-total-count response header; header-absent falls back to items.size.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryExternalSearchTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun item(id: String, name: String = "PolyLite PLA") = ExternalFilament(
        id = id,
        manufacturer = "Polymaker",
        name = name,
        material = "PLA",
    )

    @Test
    fun `search returns items with totalCount from x-total-count header`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.externalFilamentResults = listOf(item("a"), item("b"))
        h.fakeApi.externalFilamentSearchTotalCount = 42

        val outcome = h.repository.searchExternalFilaments(query = "polylite", limit = 20, offset = 0)

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        val page = (outcome as SpoolmanOutcome.Success).data
        assertEquals(listOf("a", "b"), page.items.map { it.id })
        assertEquals(42, page.totalCount)
        assertEquals(Triple("polylite", 20, 0), h.fakeApi.lastExternalFilamentSearch)
    }

    @Test
    fun `search defaults to limit 20 offset 0`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.externalFilamentResults = listOf(item("a"))

        val outcome = h.repository.searchExternalFilaments(query = "pla")

        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals(Triple("pla", 20, 0), h.fakeApi.lastExternalFilamentSearch)
    }

    @Test
    fun `search forwards explicit offset for paging`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.externalFilamentResults = listOf(item("a"))

        h.repository.searchExternalFilaments(query = "pla", limit = 50, offset = 100)

        assertEquals(Triple("pla", 50, 100), h.fakeApi.lastExternalFilamentSearch)
    }

    @Test
    fun `search falls back to items size when x-total-count header absent`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.externalFilamentResults = listOf(item("a"), item("b"))
        h.fakeApi.externalFilamentSearchTotalCount = null

        val outcome = h.repository.searchExternalFilaments(query = "polylite")

        assertTrue(outcome is SpoolmanOutcome.Success)
        val page = (outcome as SpoolmanOutcome.Success).data
        assertEquals(2, page.items.size)
        assertEquals(2, page.totalCount)
    }

    @Test
    fun `search http failure surfaces as HttpError`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.failSearchExternalFilaments = FakeSpoolmanApi.Failure.Http(500, "boom")

        val outcome = h.repository.searchExternalFilaments(query = "pla")

        assertTrue("got $outcome", outcome is SpoolmanOutcome.HttpError)
        assertEquals(500, (outcome as SpoolmanOutcome.HttpError).code)
    }

    @Test
    fun `search without configured url short-circuits to UrlNotConfigured`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder, initialUrl = "")

        val outcome = h.repository.searchExternalFilaments(query = "pla")

        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        assertTrue((outcome as SpoolmanOutcome.NetworkError).cause is UrlNotConfiguredException)
        assertTrue(h.fakeApi.callLog.isEmpty())
    }

    @Test
    fun `testConnection publishes serverInfo for version gating`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.info = SpoolmanInfo(version = "0.27.1", external_db_name = "SpoolmanDB")

        val outcome = h.repository.testConnection()

        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals("0.27.1", h.repository.serverInfo.value?.version)
        assertEquals("SpoolmanDB", h.repository.serverInfo.value?.external_db_name)
        assertTrue(h.repository.serverInfo.value.supportsExternalDbSearch())
    }

    @Test
    fun `serverInfo stays null until info is probed`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        // URL bind alone doesn't probe /info — only testConnection does today.
        assertEquals(null, h.repository.serverInfo.value)
        h.fakeApi.info = SpoolmanInfo(version = "0.27.1")
        h.repository.testConnection()
        assertEquals("0.27.1", h.repository.serverInfo.value?.version)
    }
}
