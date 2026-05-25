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
class SpoolmanRepositoryUrlChangeTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `blank initial URL leaves connectivity Unknown and no api built`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder, initialUrl = "")
        assertEquals(ConnectivityState.Unknown, h.repository.connectivity.value)
        // Any call should short-circuit:
        val outcome = h.repository.probe()
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        assertTrue((outcome as SpoolmanOutcome.NetworkError).cause is UrlNotConfiguredException)
    }

    @Test
    fun `blank to non-blank initialises Spoolman client`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder, initialUrl = "")
        h.settingsRepository.setUrl("http://test.local/")
        val outcome = h.repository.probe()
        assertEquals(SpoolmanOutcome.Success(Unit), outcome)
    }

    @Test
    fun `URL change clears caches and resets connectivity to Unknown`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.vendorList += SpoolmanVendor(id = 1, name = "V1")
        h.fakeApi.filamentList += SpoolmanFilament(id = 11, material = "PLA")
        h.fakeApi.spoolList += SpoolmanSpool(
            id = 21,
            filament = SpoolmanFilament(id = 11, material = "PLA"),
        )
        h.repository.refresh() // populate caches
        assertEquals(1, h.repository.vendors.value.size)
        assertEquals(ConnectivityState.Reachable, h.repository.connectivity.value)

        h.settingsRepository.setUrl("http://other.local/")
        assertTrue(h.repository.vendors.value.isEmpty())
        assertTrue(h.repository.filaments.value.isEmpty())
        assertTrue(h.repository.spools.value.isEmpty())
        assertEquals(ConnectivityState.Unknown, h.repository.connectivity.value)
    }

    @Test
    fun `non-blank to blank tears down api`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.repository.probe() // force Reachable
        h.settingsRepository.setUrl("")
        assertEquals(ConnectivityState.Unknown, h.repository.connectivity.value)
        val outcome = h.repository.probe()
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        assertTrue((outcome as SpoolmanOutcome.NetworkError).cause is UrlNotConfiguredException)
    }
}
