package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.usecases.NewFilamentRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Save paths: the tag's product code goes into a new filament's create, or
 * into the empty field of a filament the create matched. Save never patches
 * it onto a picked spool's filament; that is filled on Map tag
 * (VendorUidOnlyPairUseCase).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArticleNumberSavePathsTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val vendor = SpoolmanVendor(id = 7, name = "Anycubic")

    private fun filament(articleNumber: String?) = SpoolmanFilament(
        id = 11, material = "PLA", vendor = vendor, color_hex = "009CBD", article_number = articleNumber,
    )

    private fun req(articleNumber: String?) = NewFilamentRequest(
        name = "Anycubic PLA",
        vendorName = "Anycubic",
        materialName = "PLA",
        colorHex = "009CBD",
        variant = null,
        tempRanges = TempRanges(extruderMin = 190, extruderMax = 230, bedMin = 55, bedMax = 65),
        expanderOverrides = ExpanderOverrides(articleNumber = articleNumber),
    )

    private fun harness(vararg filaments: SpoolmanFilament): SpoolmanRepositoryHarness {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.vendorList += vendor
        filaments.forEach { h.fakeApi.filamentList += it }
        h.fakeApi.nextFilamentId = 100
        return h
    }

    @Test
    fun `new filament is created with the article number`() = runTest {
        val h = harness()

        h.repository.createSpoolForNewFilament(req("HPL18-106"))

        assertEquals("HPL18-106", h.fakeApi.filamentList.single { it.id == 100 }.article_number)
    }

    @Test
    fun `matched filament with no code gets the tag's code`() = runTest {
        val h = harness(filament(articleNumber = null))

        h.repository.createSpoolForNewFilament(req("HPL18-106"))

        assertTrue(h.fakeApi.callLog.none { it.startsWith("createFilament") })
        assertEquals("HPL18-106", h.fakeApi.filamentList.single().article_number)
    }

    @Test
    fun `matched filament's fill is returned on the bundle`() = runTest {
        val h = harness(filament(articleNumber = null))

        val bundle = (h.repository.createSpoolForNewFilamentBundle(req("HPL18-106")) as SpoolmanOutcome.Success).data

        val fill = bundle.articleNumberFill
        assertNotNull("matched filament's fill must be handed back", fill)
        val stored = fill!!.await()
        assertEquals("HPL18-106", (stored as SpoolmanOutcome.Success).data.article_number)
    }

    @Test
    fun `fresh filament returns no fill`() = runTest {
        val h = harness()
        val bundle = (h.repository.createSpoolForNewFilamentBundle(req("HPL18-106")) as SpoolmanOutcome.Success).data
        assertNull(bundle.articleNumberFill)
    }

    @Test
    fun `already coded filament returns no fill`() = runTest {
        val h = harness(filament(articleNumber = "AHPLRR-107"))
        val bundle = (h.repository.createSpoolForNewFilamentBundle(req("HPL18-106")) as SpoolmanOutcome.Success).data
        assertNull(bundle.articleNumberFill)
    }

    @Test
    fun `matched filament keeps its own code`() = runTest {
        val h = harness(filament(articleNumber = "AHPLRR-107"))

        h.repository.createSpoolForNewFilament(req("HPL18-106"))

        assertEquals("AHPLRR-107", h.fakeApi.filamentList.single().article_number)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("patchFilament(") })
    }

    @Test
    fun `existing spool Save does not touch the article number`() = runTest {
        val f = filament(articleNumber = null)
        val h = harness(f)
        h.fakeApi.spoolList += SpoolmanSpool(id = 3, filament = f)

        h.repository.applyOverridesToFilamentOfSpool(3, ExpanderOverrides(articleNumber = "HPL18-106"))

        assertNull(h.fakeApi.filamentList.single().article_number)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("patchFilament(") })
    }
}
