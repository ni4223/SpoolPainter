package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.primitives.CardUid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        uid: String = "abcd",
    ): NewSpoolRequest = NewSpoolRequest(
        vendorName = vendor,
        materialName = material,
        colorHex = color,
        variant = variant,
        tempRanges = TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60),
        cardUid = CardUid(uid),
    )

    @Test
    fun `vendor lookup hit reuses existing vendor (no POST)`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.vendorList += SpoolmanVendor(id = 7, name = "Polymaker")
        val outcome = h.repository.createSpoolForNewFilament(req())
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createVendor") })
    }

    @Test
    fun `vendor lookup miss issues POST`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        val outcome = h.repository.createSpoolForNewFilament(req())
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.any { it.startsWith("createVendor") })
    }

    @Test
    fun `case-insensitive vendor match`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.vendorList += SpoolmanVendor(id = 7, name = "polymaker")
        val outcome = h.repository.createSpoolForNewFilament(req(vendor = "POLYMAKER"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createVendor") })
    }

    @Test
    fun `filament lookup hit reuses existing filament`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
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
    fun `filament lookup miss issues POST`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.vendorList += SpoolmanVendor(id = 7, name = "Polymaker")
        val outcome = h.repository.createSpoolForNewFilament(req())
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.any { it.startsWith("createFilament") })
    }

    @Test
    fun `variant null and empty are equivalent for filament match`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        val vendor = SpoolmanVendor(id = 7, name = "Polymaker")
        h.fakeApi.vendorList += vendor
        h.fakeApi.filamentList += SpoolmanFilament(
            id = 11, name = null, material = "PLA", vendor = vendor, color_hex = "FF0000",
        )
        val outcome = h.repository.createSpoolForNewFilament(req(variant = ""))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createFilament") })
    }

    @Test
    fun `variant whitespace-only normalised to null`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        val vendor = SpoolmanVendor(id = 7, name = "Polymaker")
        h.fakeApi.vendorList += vendor
        h.fakeApi.filamentList += SpoolmanFilament(
            id = 11, name = null, material = "PLA", vendor = vendor, color_hex = "FF0000",
        )
        val outcome = h.repository.createSpoolForNewFilament(req(variant = "   "))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createFilament") })
    }

    @Test
    fun `spool POST sets lot_nr to card_uid prefix`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        val outcome = h.repository.createSpoolForNewFilament(req(uid = "abcdef"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals("card_uid:abcdef", (outcome as SpoolmanOutcome.Success).data.lot_nr)
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
    fun `fail at filament short-circuits chain (vendor stays committed)`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.failCreateFilament = FakeSpoolmanApi.Failure.Http(503, "sql err")
        val outcome = h.repository.createSpoolForNewFilament(req())
        assertTrue(outcome is SpoolmanOutcome.HttpError)
        // Vendor created remains in Spoolman (Q11=A — no rollback)
        assertEquals(1, h.fakeApi.vendorList.size)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createSpool") })
    }

    @Test
    fun `fail at spool short-circuits (vendor and filament stay committed)`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.failCreateSpool = FakeSpoolmanApi.Failure.Http(500, "boom")
        val outcome = h.repository.createSpoolForNewFilament(req())
        assertTrue(outcome is SpoolmanOutcome.HttpError)
        assertEquals(1, h.fakeApi.vendorList.size)
        assertEquals(1, h.fakeApi.filamentList.size)
    }

    @Test
    fun `empty UID rejected without HTTP`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        val outcome = h.repository.createSpoolForNewFilament(req(uid = ""))
        assertTrue(outcome is SpoolmanOutcome.NetworkError)
        assertTrue((outcome as SpoolmanOutcome.NetworkError).cause is IllegalArgumentException)
        assertTrue(h.fakeApi.callLog.isEmpty())
    }
}
