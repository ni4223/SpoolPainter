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
class ConnectivityStateTransitionTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `BR-U3-CONN-1 initial value is Unknown`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder, initialUrl = "")
        assertEquals(ConnectivityState.Unknown, h.repository.connectivity.value)
    }

    @Test
    fun `BR-U3-CONN-2 successful 2xx transitions to Reachable`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.repository.testConnection()
        assertEquals(ConnectivityState.Reachable, h.repository.connectivity.value)
    }

    @Test
    fun `BR-U3-CONN-3 HTTP 4xx_5xx still transitions to Reachable`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.failGetInfo = FakeSpoolmanApi.Failure.Http(500, "boom")
        h.repository.testConnection()
        assertEquals(ConnectivityState.Reachable, h.repository.connectivity.value)
    }

    @Test
    fun `BR-U3-CONN-4 IOException transitions to Unreachable with reason`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.failGetInfo = FakeSpoolmanApi.Failure.Throws(IOException("dns"))
        h.repository.testConnection()
        val state = h.repository.connectivity.value
        assertTrue(state is ConnectivityState.Unreachable)
        assertEquals("dns", (state as ConnectivityState.Unreachable).reason)
    }

    @Test
    fun `BR-U3-CONN-5 URL not configured short-circuit sets Unknown`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder, initialUrl = "")
        h.repository.testConnection()
        assertEquals(ConnectivityState.Unknown, h.repository.connectivity.value)
    }

    @Test
    fun `BR-U3-CONN-6 ParseError leaves connectivity unchanged`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.repository.testConnection() // → Reachable
        h.fakeApi.failGetInfo = FakeSpoolmanApi.Failure.Throws(JsonSyntaxException("bad"))
        h.repository.testConnection()
        assertEquals(ConnectivityState.Reachable, h.repository.connectivity.value)
    }

    @Test
    fun `BR-U3-CONN-7 atomic transition before outcome is returned`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        val outcome: SpoolmanOutcome<*> = h.repository.testConnection()
        // Both must be observable post-call.
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals(ConnectivityState.Reachable, h.repository.connectivity.value)
    }
}
