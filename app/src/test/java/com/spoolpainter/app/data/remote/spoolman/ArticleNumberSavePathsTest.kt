package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
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
 * Save paths: the tag's product code goes into a new filament's create, and
 * Save never patches it onto an existing filament. Filling an existing
 * filament's empty field happens only on Map tag (VendorUidOnlyPairUseCase).
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
    fun `reused filament is left alone`() = runTest {
        val h = harness(filament(articleNumber = null))

        h.repository.createSpoolForNewFilament(req("HPL18-106"))

        assertTrue(h.fakeApi.callLog.none { it.startsWith("createFilament") })
        assertNull(h.fakeApi.filamentList.single().article_number)
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
