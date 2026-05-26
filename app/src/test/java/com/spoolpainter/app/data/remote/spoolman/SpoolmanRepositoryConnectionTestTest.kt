package com.spoolpainter.app.data.remote.spoolman

import com.google.gson.JsonSyntaxException
import com.spoolpainter.app.domain.models.SpoolmanInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryConnectionTestTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `testConnection returnsVersion onInfo200`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.info = SpoolmanInfo(version = "0.21.0")
        val outcome = h.repository.testConnection()
        assertEquals(SpoolmanOutcome.Success("0.21.0"), outcome)
        assertEquals(ConnectivityState.Reachable, h.repository.connectivity.value)
    }

    @Test
    fun `testConnection returnsHttpError onInfo5xx`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.failGetInfo = FakeSpoolmanApi.Failure.Http(503, "down")
        val outcome = h.repository.testConnection()
        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertEquals(503, (outcome as SpoolmanOutcome.HttpError).code)
        assertEquals(ConnectivityState.Reachable, h.repository.connectivity.value)
    }

    @Test
    fun `testConnection returnsNetworkError onIoException`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.failGetInfo = FakeSpoolmanApi.Failure.Throws(IOException("dns"))
        val outcome = h.repository.testConnection()
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        val state = h.repository.connectivity.value
        assertTrue(state is ConnectivityState.Unreachable)
        assertEquals("dns", (state as ConnectivityState.Unreachable).reason)
    }

    @Test
    fun `testConnection returnsParseError onJsonSyntaxException`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.repository.testConnection() // success → Reachable
        h.fakeApi.failGetInfo = FakeSpoolmanApi.Failure.Throws(JsonSyntaxException("bad"))
        val outcome = h.repository.testConnection()
        assertTrue(outcome is SpoolmanOutcome.ParseError)
        assertEquals(ConnectivityState.Reachable, h.repository.connectivity.value)
    }

    @Test
    fun `testConnection withBlankUrl shortCircuitsToNetworkErrorUrlNotConfigured`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder, initialUrl = "")
        val outcome = h.repository.testConnection()
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        assertTrue((outcome as SpoolmanOutcome.NetworkError).cause is UrlNotConfiguredException)
        assertEquals(ConnectivityState.Unknown, h.repository.connectivity.value)
        assertTrue(!h.fakeApi.callLog.contains("getInfo"))
    }
}
