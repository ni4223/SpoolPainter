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

@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryCreateChainTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun req(
        vendor: String = "Polymaker",
        material: String = "PLA",
        color: String = "FF0000",
        variant: String? = null,
    ): NewFilamentRequest = NewFilamentRequest(
        name = "$vendor $material",
        vendorName = vendor,
        materialName = material,
        colorHex = color,
        variant = variant,
        tempRanges = TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60),
    )

    private fun preregisterExtras(api: FakeSpoolmanApi) {
        // Spool create no longer sets card_uids, so only filament needs the
        // variant field pre-registered for happy-path tests.
        api.filamentExtraFields += "variant"
    }

    @Test
    fun `createSpoolForNewFilament happyPath emitsExtraVariantOnFilamentAndNoCardUidsOnSpool`() =
        runTest {
            val h = SpoolmanRepositoryHarness(tempFolder)
            preregisterExtras(h.fakeApi)
            val outcome = h.repository.createSpoolForNewFilament(req(variant = "Matte"))
            assertTrue(outcome is SpoolmanOutcome.Success)
            val createdFilament = h.fakeApi.filamentList.single()
            assertEquals("\"Matte\"", createdFilament.extra?.get("variant"))
            val createdSpool = (outcome as SpoolmanOutcome.Success).data
            // The spool is created without card_uids; the use case PATCHes it
            // in via appendCardUidToSpool after the tap reveals the UID.
            assertNull(createdSpool.extra?.get("card_uids"))
            assertNull(createdSpool.lot_nr)
        }

    @Test
    fun `createSpoolForNewFilament omitsExtraVariant whenVariantNullOrBlank`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        preregisterExtras(h.fakeApi)
        val outcome = h.repository.createSpoolForNewFilament(req(variant = "   "))
        assertTrue(outcome is SpoolmanOutcome.Success)
        val createdFilament = h.fakeApi.filamentList.single()
        assertTrue(createdFilament.extra == null || !createdFilament.extra!!.containsKey("variant"))
    }

    @Test
    fun `createSpoolForNewFilament reusesExistingVendor`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        preregisterExtras(h.fakeApi)
        h.fakeApi.vendorList += SpoolmanVendor(id = 7, name = "Polymaker")
        val outcome = h.repository.createSpoolForNewFilament(req())
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createVendor") })
    }

    @Test
    fun `createSpoolForNewFilament lazyBootstrap onFilament400`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        // filament side NOT pre-registered → first createFilament 400, bootstrap registers, retry succeeds.
        val outcome = h.repository.createSpoolForNewFilament(req(variant = "Matte"))
        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.contains("postField(filament,variant)"))
    }

    @Test
    fun `createSpoolForNewFilament doesNotSetLotNr`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        preregisterExtras(h.fakeApi)
        val outcome = h.repository.createSpoolForNewFilament(req())
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertNull((outcome as SpoolmanOutcome.Success).data.lot_nr)
    }

    @Test
    fun `vendor lookup miss issues POST`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        preregisterExtras(h.fakeApi)
        val outcome = h.repository.createSpoolForNewFilament(req())
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.any { it.startsWith("createVendor") })
    }

    @Test
    fun `case-insensitive vendor match`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        preregisterExtras(h.fakeApi)
        h.fakeApi.vendorList += SpoolmanVendor(id = 7, name = "polymaker")
        val outcome = h.repository.createSpoolForNewFilament(req(vendor = "POLYMAKER"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createVendor") })
    }

    @Test
    fun `filament lookup hit reuses existing filament`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        preregisterExtras(h.fakeApi)
        val vendor = SpoolmanVendor(id = 7, name = "Polymaker")
        h.fakeApi.vendorList += vendor
        h.fakeApi.filamentList += SpoolmanFilament(
            id = 11, name = null, material = "PLA", vendor = vendor, color_hex = "FF0000",
        )
        val outcome = h.repository.createSpoolForNewFilament(req())
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createFilament") })
    }

    @Test
    fun `fail at vendor short-circuits chain`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.failListVendors = FakeSpoolmanApi.Failure.Http(500, "boom")
        val outcome = h.repository.createSpoolForNewFilament(req())
        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("listFilaments") })
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createSpool") })
    }

    @Test
    fun `empty vendor rejected without HTTP`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        val outcome = h.repository.createSpoolForNewFilament(req(vendor = ""))
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        assertTrue((outcome as SpoolmanOutcome.NetworkError).cause is IllegalArgumentException)
        assertTrue(h.fakeApi.callLog.isEmpty())
    }
}
