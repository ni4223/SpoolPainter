package com.spoolpainter.app.data.remote.spoolman

import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryProbeTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `probe success returns Success and sets connectivity Reachable`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        val outcome = h.repository.probe()
        assertEquals(SpoolmanOutcome.Success(Unit), outcome)
        assertEquals(ConnectivityState.Reachable, h.repository.connectivity.value)
    }

    @Test
    fun `probe HTTP error returns HttpError and sets connectivity Reachable`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.failGetInfo = FakeSpoolmanApi.Failure.Http(503, "down")
        val outcome = h.repository.probe()
        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertEquals(503, (outcome as SpoolmanOutcome.HttpError).code)
        assertEquals(ConnectivityState.Reachable, h.repository.connectivity.value)
    }

    @Test
    fun `probe IOException returns NetworkError and sets connectivity Unreachable`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.failGetInfo = FakeSpoolmanApi.Failure.Throws(IOException("dns"))
        val outcome = h.repository.probe()
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        val state = h.repository.connectivity.value
        assertTrue(state is ConnectivityState.Unreachable)
        assertEquals("dns", (state as ConnectivityState.Unreachable).reason)
    }

    @Test
    fun `probe JsonSyntaxException returns ParseError and connectivity unchanged`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        // Seed connectivity to Reachable first via a successful call, then trigger parse fault.
        h.repository.probe() // success → Reachable
        h.fakeApi.failGetInfo = FakeSpoolmanApi.Failure.Throws(JsonSyntaxException("bad"))
        val outcome = h.repository.probe()
        assertTrue(outcome is SpoolmanOutcome.ParseError)
        assertEquals(ConnectivityState.Reachable, h.repository.connectivity.value)
    }

    @Test
    fun `probe with blank URL short-circuits to NetworkError UrlNotConfigured and connectivity Unknown`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder, initialUrl = "")
        val outcome = h.repository.probe()
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        val cause = (outcome as SpoolmanOutcome.NetworkError).cause
        assertTrue(cause is UrlNotConfiguredException)
        assertEquals(ConnectivityState.Unknown, h.repository.connectivity.value)
        // No HTTP call fired.
        assertTrue(!h.fakeApi.callLog.contains("getInfo"))
    }
}
