package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.usecases.NewFilamentRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * SpoolmanDB import create-chain: external_id linkage for vendor + filament
 * POSTs, cached-filament reuse on repeat imports, and the diameterMm
 * override behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryExternalFilamentCreateChainTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun req(
        externalId: String? = null,
        diameterMm: Double? = null,
        color: String = "FF0000",
    ): NewFilamentRequest = NewFilamentRequest(
        name = "Polymaker PolyLite PLA",
        vendorName = "Polymaker",
        materialName = "PLA",
        colorHex = color,
        variant = null,
        tempRanges = TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60),
        externalFilamentId = externalId,
        diameterMm = diameterMm,
    )

    @Test
    fun `externalId with cached filament match reuses it without POST`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.filamentExtraFields += "variant"
        val vendor = SpoolmanVendor(id = 7, name = "Polymaker")
        h.fakeApi.vendorList += vendor
        h.fakeApi.filamentList += SpoolmanFilament(
            id = 11,
            name = "PolyLite PLA",
            material = "PLA",
            vendor = vendor,
            // Deliberately different colour than the request — the
            // external_id match must win over the fuzzy matcher.
            color_hex = "00FF00",
            external_id = "polymaker-polylite-pla",
        )
        h.repository.refresh() // populate the repo's filament cache

        val outcome = h.repository.createSpoolForNewFilament(req(externalId = "polymaker-polylite-pla"))

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createFilament") })
        assertEquals(1, h.fakeApi.filamentList.size)
        assertEquals(11, (outcome as SpoolmanOutcome.Success).data.filament.id)
    }

    @Test
    fun `externalId absent from stale cache but present in live list reuses and backfills cache`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.filamentExtraFields += "variant"
        val vendor = SpoolmanVendor(id = 7, name = "Polymaker")
        h.fakeApi.vendorList += vendor
        h.fakeApi.filamentList += SpoolmanFilament(
            id = 11,
            name = "PolyLite PLA",
            material = "PLA",
            vendor = vendor,
            // Deliberately different colour than the request — the
            // external_id live-list match must win over the fuzzy matcher.
            color_hex = "00FF00",
            external_id = "polymaker-polylite-pla",
        )
        // NOTE: no refresh() — the repo's filament cache is still empty
        // (stale), simulating the same external filament imported through
        // the Spoolman web UI after this app's last refresh.

        val outcome = h.repository.createSpoolForNewFilament(req(externalId = "polymaker-polylite-pla"))

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createFilament") })
        assertEquals(1, h.fakeApi.filamentList.size)
        assertEquals(11, (outcome as SpoolmanOutcome.Success).data.filament.id)
        // Cache backfilled with exactly one entry so the next call takes the
        // early cache shortcut instead of the network path.
        val cached = h.repository.filaments.value.filter { it.external_id == "polymaker-polylite-pla" }
        assertEquals(listOf(11), cached.map { it.id })
    }

    @Test
    fun `externalId in neither cache nor live list POSTs carrying external_id`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.filamentExtraFields += "variant"
        val vendor = SpoolmanVendor(id = 7, name = "Polymaker")
        h.fakeApi.vendorList += vendor
        // Unrelated live filament: fuzzy matcher rejects it (different
        // material + colour) and its external_id differs → must POST.
        h.fakeApi.filamentList += SpoolmanFilament(
            id = 11,
            name = null,
            material = "PETG",
            vendor = vendor,
            color_hex = "0000FF",
            external_id = "some-other-slug",
        )

        val outcome = h.repository.createSpoolForNewFilament(req(externalId = "polymaker-polylite-pla"))

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertEquals("polymaker-polylite-pla", h.fakeApi.lastCreateFilamentBody!!.external_id)
        assertEquals(2, h.fakeApi.filamentList.size)
    }

    @Test
    fun `externalId without match POSTs filament carrying external_id and request diameter`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.filamentExtraFields += "variant"

        val outcome = h.repository.createSpoolForNewFilament(
            req(externalId = "polymaker-polylite-pla", diameterMm = 2.85),
        )

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        val body = h.fakeApi.lastCreateFilamentBody
        assertTrue("createFilament body not captured", body != null)
        assertEquals("polymaker-polylite-pla", body!!.external_id)
        assertEquals(2.85f, body.diameter)
        // density/weight defaults unchanged (no explicit overrides).
        assertEquals(1.24f, body.density) // PLA preset
        assertEquals(1000f, body.weight)
        // The fake persisted the external_id on the created record.
        assertEquals("polymaker-polylite-pla", h.fakeApi.filamentList.single().external_id)
    }

    @Test
    fun `diameter falls back to 1_75 default when diameterMm null`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.filamentExtraFields += "variant"

        h.repository.createSpoolForNewFilament(req(externalId = "slug"))

        assertEquals(1.75f, h.fakeApi.lastCreateFilamentBody!!.diameter)
    }

    @Test
    fun `new vendor POST carries external_id equal to vendor name`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.filamentExtraFields += "variant"

        val outcome = h.repository.createSpoolForNewFilament(req(externalId = "polymaker-polylite-pla"))

        assertTrue(outcome is SpoolmanOutcome.Success)
        val body = h.fakeApi.lastCreateVendorBody
        assertTrue("createVendor body not captured", body != null)
        assertEquals("Polymaker", body!!.name)
        assertEquals("Polymaker", body.external_id)
    }

    @Test
    fun `existing vendor name-match skips vendor POST entirely`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.filamentExtraFields += "variant"
        h.fakeApi.vendorList += SpoolmanVendor(id = 7, name = "Polymaker")

        val outcome = h.repository.createSpoolForNewFilament(req(externalId = "polymaker-polylite-pla"))

        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createVendor") })
        assertNull(h.fakeApi.lastCreateVendorBody)
    }

    @Test
    fun `without externalFilamentId vendor and filament POSTs carry no external_id`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.filamentExtraFields += "variant"

        val outcome = h.repository.createSpoolForNewFilament(req())

        assertTrue(outcome is SpoolmanOutcome.Success)
        assertNull(h.fakeApi.lastCreateVendorBody!!.external_id)
        assertNull(h.fakeApi.lastCreateFilamentBody!!.external_id)
        assertEquals(1.75f, h.fakeApi.lastCreateFilamentBody!!.diameter)
    }
}
