package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * F-6 (v2.0.3) — refreshIfStale throttle + force semantics. The throttle
 * window is 5s wall-clock, so back-to-back calls inside a runTest block
 * land well under the threshold and reliably exercise the suppress path.
 * Force=true must always bypass the throttle (used by user-initiated PTR).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryRefreshIfStaleTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun seedHarness(): SpoolmanRepositoryHarness {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.vendorList += SpoolmanVendor(id = 1, name = "V1")
        h.fakeApi.filamentList += SpoolmanFilament(id = 11, material = "PLA")
        h.fakeApi.spoolList += SpoolmanSpool(
            id = 21,
            filament = SpoolmanFilament(id = 11, material = "PLA"),
        )
        return h
    }

    private fun refreshHttpCount(callLog: List<String>): Int =
        callLog.count { it == "listVendors" }

    @Test
    fun `first call runs full refresh`() = runTest {
        val h = seedHarness()
        val outcome = h.repository.refreshIfStale()
        assertEquals(SpoolmanOutcome.Success(Unit), outcome)
        assertEquals(1, refreshHttpCount(h.fakeApi.callLog))
    }

    @Test
    fun `second call within throttle window is suppressed`() = runTest {
        val h = seedHarness()
        h.repository.refreshIfStale()
        h.fakeApi.callLog.clear()
        val outcome = h.repository.refreshIfStale()
        // Throttle returns Success(Unit) silently — no HTTP fired.
        assertEquals(SpoolmanOutcome.Success(Unit), outcome)
        assertEquals(0, refreshHttpCount(h.fakeApi.callLog))
    }

    @Test
    fun `force=true bypasses throttle`() = runTest {
        val h = seedHarness()
        h.repository.refreshIfStale()
        h.fakeApi.callLog.clear()
        val outcome = h.repository.refreshIfStale(force = true)
        assertEquals(SpoolmanOutcome.Success(Unit), outcome)
        assertEquals(1, refreshHttpCount(h.fakeApi.callLog))
    }

    @Test
    fun `concurrent calls collapse to one refresh via mutex`() = runTest {
        val h = seedHarness()
        // Three concurrent (force=false) calls — only the first should
        // hit the wire; the rest serialise behind the mutex and find a
        // fresh lastRefreshEpochMs already set, returning Success(Unit).
        val outcomes = listOf(
            async { h.repository.refreshIfStale() },
            async { h.repository.refreshIfStale() },
            async { h.repository.refreshIfStale() },
        ).awaitAll()
        outcomes.forEach { assertEquals(SpoolmanOutcome.Success(Unit), it) }
        assertEquals(1, refreshHttpCount(h.fakeApi.callLog))
    }

    @Test
    fun `no URL configured returns NetworkError without firing HTTP`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder, initialUrl = "")
        val outcome = h.repository.refreshIfStale()
        // urlNotConfigured() wraps UrlNotConfiguredException in NetworkError.
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        assertTrue(refreshHttpCount(h.fakeApi.callLog) == 0)
    }

    @Test
    fun `failed refresh does not stamp lastRefreshEpochMs so retry runs`() = runTest {
        val h = seedHarness()
        h.fakeApi.failListVendors = FakeSpoolmanApi.Failure.Http(500, "boom")
        val first = h.repository.refreshIfStale()
        assertTrue(first is SpoolmanOutcome.HttpError)
        // Clear the failure + log so we can detect whether the second call
        // re-attempted.
        h.fakeApi.failListVendors = null
        h.fakeApi.callLog.clear()
        val second = h.repository.refreshIfStale()
        // No throttle skip — failed refresh didn't update the timestamp,
        // so the retry must attempt the real refresh.
        assertEquals(SpoolmanOutcome.Success(Unit), second)
        assertEquals(1, refreshHttpCount(h.fakeApi.callLog))
    }
}
