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

/**
 * U8-Δ-1 — createSpoolForExistingFilament tests. Q-U8-12=A: separate method
 * from create-and-pair (no matcher / no filament POST). PATCH idempotency
 * cascades through to overrides — only-changed-fields go on the wire.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryCreateForExistingFilamentTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun primeFilament(h: SpoolmanRepositoryHarness, filament: SpoolmanFilament) {
        h.fakeApi.vendorList += SpoolmanVendor(id = 1, name = "Polymaker")
        h.fakeApi.filamentList += filament
        h.fakeApi.nextFilamentId = filament.id + 1
        kotlinx.coroutines.runBlocking { h.repository.refresh() }
        h.fakeApi.callLog.clear()
    }

    private val baseFilament = SpoolmanFilament(
        id = 5,
        name = "Polymaker PLA Matte",
        material = "PLA",
        vendor = SpoolmanVendor(id = 1, name = "Polymaker"),
        color_hex = "FF0000",
        density = 1.24f,
        diameter = 1.75f,
        weight = 1000f,
    )

    @Test
    fun `happy path — no expander deltas → getFilament + createSpool, no patch`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        primeFilament(h, baseFilament)

        val outcome = h.repository.createSpoolForExistingFilament(5, ExpanderOverrides.EMPTY)

        assertTrue(outcome is SpoolmanOutcome.Success)
        val getCalls = h.fakeApi.callLog.filter { it.startsWith("getFilament(") }
        val patchCalls = h.fakeApi.callLog.filter { it.startsWith("patchFilament(") }
        val createCalls = h.fakeApi.callLog.filter { it.startsWith("createSpool(") }
        assertEquals(1, getCalls.size)
        assertEquals(0, patchCalls.size)
        assertEquals(1, createCalls.size)
    }

    @Test
    fun `happy path — expander deltas → patchFilament for changed fields then createSpool`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        primeFilament(h, baseFilament)

        val outcome = h.repository.createSpoolForExistingFilament(
            5,
            ExpanderOverrides(weight = 750f, price = 19.99f),
        )

        assertTrue(outcome is SpoolmanOutcome.Success)
        val patch = h.fakeApi.callLog.single { it.startsWith("patchFilament(") }
        assertTrue(patch.contains("weight=750"))
        assertTrue(patch.contains("price=19.99"))
        // Density/diameter match cache; absent from PATCH body.
        assertTrue(patch.contains("density=null"))
        assertTrue(patch.contains("diameter=null"))
        assertEquals(1, h.fakeApi.callLog.count { it.startsWith("createSpool(") })
    }

    @Test
    fun `getFilament 404 → HttpError, no spool created`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        primeFilament(h, baseFilament)
        h.fakeApi.failGetFilament = FakeSpoolmanApi.Failure.Http(404, "missing")

        val outcome = h.repository.createSpoolForExistingFilament(5, ExpanderOverrides.EMPTY)

        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertEquals(404, (outcome as SpoolmanOutcome.HttpError).code)
        assertEquals(0, h.fakeApi.callLog.count { it.startsWith("createSpool(") })
    }

    @Test
    fun `patchFilament fails → HttpError, no spool created (fail-fast)`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        primeFilament(h, baseFilament)
        h.fakeApi.failPatchFilament = FakeSpoolmanApi.Failure.Http(422, "validation")

        val outcome = h.repository.createSpoolForExistingFilament(
            5,
            ExpanderOverrides(weight = 750f),
        )

        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertEquals(0, h.fakeApi.callLog.count { it.startsWith("createSpool(") })
    }

    @Test
    fun `createSpool fails after patch → HttpError, PATCH already happened (partial state acceptable)`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        primeFilament(h, baseFilament)
        h.fakeApi.failCreateSpool = FakeSpoolmanApi.Failure.Http(500, "server")

        val outcome = h.repository.createSpoolForExistingFilament(
            5,
            ExpanderOverrides(weight = 750f),
        )

        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertEquals(1, h.fakeApi.callLog.count { it.startsWith("patchFilament(") })
        // Cache was updated by the successful PATCH; partial state is acceptable per delta §2.
        assertEquals(750f, h.repository.filaments.value.single { it.id == 5 }.weight)
    }
}
