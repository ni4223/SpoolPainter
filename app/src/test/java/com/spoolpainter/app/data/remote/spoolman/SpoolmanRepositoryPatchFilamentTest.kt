package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanVendor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
 * U8-Δ-3 — PATCH filament idempotency tests. Per Q-U8-13=A: repository
 * reads the cache, builds a sparse body containing only fields whose stored
 * value differs, and skips the HTTP call entirely if everything matches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryPatchFilamentTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun primeFilamentInCache(
        h: SpoolmanRepositoryHarness,
        cached: SpoolmanFilament,
    ) {
        h.fakeApi.vendorList += SpoolmanVendor(id = 1, name = "Polymaker")
        h.fakeApi.filamentList += cached
        h.fakeApi.nextFilamentId = (cached.id + 1)
        kotlinx.coroutines.runBlocking { h.repository.refresh() }
        h.fakeApi.callLog.clear()
    }

    private val cachedFilament = SpoolmanFilament(
        id = 7,
        name = "Polymaker PLA Matte",
        material = "PLA",
        vendor = SpoolmanVendor(id = 1, name = "Polymaker"),
        color_hex = "FF0000",
        density = 1.24f,
        diameter = 1.75f,
        weight = 1000f,
        spool_weight = 200f,
        price = null,
    )

    @Test
    fun `patchFilament — all values differ → PATCH issued with diff fields only`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        primeFilamentInCache(h, cachedFilament)

        val outcome = h.repository.patchFilament(
            7,
            PatchFilamentBody(weight = 750f, spool_weight = 250f, price = 19.99f),
        )

        assertTrue(outcome is SpoolmanOutcome.Success)
        val patchCall = h.fakeApi.callLog.singleOrNull { it.startsWith("patchFilament(") }
        assertTrue("expected single patchFilament call, got ${h.fakeApi.callLog}", patchCall != null)
        // Only differing fields go on the wire — density/diameter omitted.
        assertTrue(patchCall!!.contains("weight=750"))
        assertTrue(patchCall.contains("spool_weight=250"))
        assertTrue(patchCall.contains("price=19.99"))
        assertTrue(patchCall.contains("density=null"))
        assertTrue(patchCall.contains("diameter=null"))
    }

    @Test
    fun `patchFilament — all values match → no HTTP call, returns cached`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        primeFilamentInCache(h, cachedFilament)

        val outcome = h.repository.patchFilament(
            7,
            PatchFilamentBody(
                density = 1.24f,
                diameter = 1.75f,
                weight = 1000f,
                spool_weight = 200f,
            ),
        )

        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals(cachedFilament, (outcome as SpoolmanOutcome.Success).data)
        assertTrue(
            "expected no patchFilament call, got ${h.fakeApi.callLog}",
            h.fakeApi.callLog.none { it.startsWith("patchFilament(") },
        )
    }

    @Test
    fun `patchFilament — partial diff → HTTP body contains only changed field`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        primeFilamentInCache(h, cachedFilament)

        val outcome = h.repository.patchFilament(7, PatchFilamentBody(weight = 750f))

        assertTrue(outcome is SpoolmanOutcome.Success)
        val patchCall = h.fakeApi.callLog.single { it.startsWith("patchFilament(") }
        assertTrue(patchCall.contains("weight=750"))
        assertTrue(patchCall.contains("density=null"))
        assertTrue(patchCall.contains("diameter=null"))
    }

    @Test
    fun `patchFilament — 4xx → HttpError, cache unchanged`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        primeFilamentInCache(h, cachedFilament)
        h.fakeApi.failPatchFilament = FakeSpoolmanApi.Failure.Http(422, "validation")

        val outcome = h.repository.patchFilament(7, PatchFilamentBody(weight = 999f))

        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertEquals(422, (outcome as SpoolmanOutcome.HttpError).code)
        // Cache unchanged.
        assertEquals(1000f, h.repository.filaments.value.single { it.id == 7 }.weight)
    }

    @Test
    fun `patchFilament — 5xx → HttpError, cache unchanged`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        primeFilamentInCache(h, cachedFilament)
        h.fakeApi.failPatchFilament = FakeSpoolmanApi.Failure.Http(503, "down")

        val outcome = h.repository.patchFilament(7, PatchFilamentBody(weight = 999f))

        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertEquals(503, (outcome as SpoolmanOutcome.HttpError).code)
        assertEquals(1000f, h.repository.filaments.value.single { it.id == 7 }.weight)
    }

    @Test
    fun `patchFilament — IOException → NetworkError, cache unchanged`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        primeFilamentInCache(h, cachedFilament)
        h.fakeApi.failPatchFilament = FakeSpoolmanApi.Failure.Throws(IOException("network down"))

        val outcome = h.repository.patchFilament(7, PatchFilamentBody(weight = 999f))

        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        assertEquals(1000f, h.repository.filaments.value.single { it.id == 7 }.weight)
    }
}
