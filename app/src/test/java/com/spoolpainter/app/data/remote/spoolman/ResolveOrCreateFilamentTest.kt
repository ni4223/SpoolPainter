package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.usecases.NewFilamentRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Covers FR-U6b-Δ-4 — filament matcher canonicalisation. Each retry on the
 * "same" filament must hit the existing row (no duplicate creates) when the
 * inputs differ only by colour-hex prefix/case or variant blank/case.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResolveOrCreateFilamentTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun req(
        color: String = "FF0000",
        variant: String? = null,
    ): NewFilamentRequest = NewFilamentRequest(
        name = "Polymaker PLA",
        vendorName = "Polymaker",
        materialName = "PLA",
        colorHex = color,
        variant = variant,
        tempRanges = TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60),
    )

    private fun seed(h: SpoolmanRepositoryHarness, filament: SpoolmanFilament): SpoolmanVendor {
        val vendor = filament.vendor!!
        h.fakeApi.vendorList += vendor
        h.fakeApi.filamentList += filament
        h.fakeApi.filamentExtraFields += "variant"
        return vendor
    }

    @Test
    fun `lowercase color hex matches uppercase request`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seed(
            h,
            SpoolmanFilament(
                id = 11, name = null, material = "PLA",
                vendor = SpoolmanVendor(id = 7, name = "Polymaker"),
                color_hex = "ff0000",
            ),
        )
        val outcome = h.repository.createSpoolForNewFilament(req(color = "FF0000"))
        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createFilament") })
    }

    @Test
    fun `hash-prefixed color hex matches stripped`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seed(
            h,
            SpoolmanFilament(
                id = 11, name = null, material = "PLA",
                vendor = SpoolmanVendor(id = 7, name = "Polymaker"),
                color_hex = "#FF0000",
            ),
        )
        val outcome = h.repository.createSpoolForNewFilament(req(color = "FF0000"))
        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createFilament") })
    }

    @Test
    fun `null variant matches null request`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seed(
            h,
            SpoolmanFilament(
                id = 11, name = null, material = "PLA",
                vendor = SpoolmanVendor(id = 7, name = "Polymaker"),
                color_hex = "FF0000", extra = null,
            ),
        )
        val outcome = h.repository.createSpoolForNewFilament(req(variant = null))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createFilament") })
    }

    @Test
    fun `blank variant matches null request`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seed(
            h,
            SpoolmanFilament(
                id = 11, name = null, material = "PLA",
                vendor = SpoolmanVendor(id = 7, name = "Polymaker"),
                color_hex = "FF0000",
                extra = mapOf("variant" to "\"\""),
            ),
        )
        val outcome = h.repository.createSpoolForNewFilament(req(variant = null))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createFilament") })
    }

    @Test
    fun `case-insensitive variant equality`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seed(
            h,
            SpoolmanFilament(
                id = 11, name = null, material = "PLA",
                vendor = SpoolmanVendor(id = 7, name = "Polymaker"),
                color_hex = "FF0000",
                extra = mapOf("variant" to "\"matte\""),
            ),
        )
        val outcome = h.repository.createSpoolForNewFilament(req(variant = "Matte"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("createFilament") })
    }

    @Test
    fun `different colour or variant creates fresh filament`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seed(
            h,
            SpoolmanFilament(
                id = 11, name = null, material = "PLA",
                vendor = SpoolmanVendor(id = 7, name = "Polymaker"),
                color_hex = "FF0000",
                extra = mapOf("variant" to "\"matte\""),
            ),
        )
        val outcome = h.repository.createSpoolForNewFilament(req(color = "00FF00", variant = "Matte"))
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.any { it.startsWith("createFilament") })
        assertEquals(2, h.fakeApi.filamentList.size)
    }
}
