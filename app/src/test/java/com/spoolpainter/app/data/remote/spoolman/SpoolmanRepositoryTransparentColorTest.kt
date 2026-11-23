package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.ui.screens.main.FormMapping
import com.spoolpainter.app.ui.screens.main.toExpanderOverrides
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * U33 / UI-75: a Save must never rewrite a transparent (RRGGBBAA) Spoolman
 * colour the user didn't change, and a changed colour keeps the stored alpha.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryTransparentColorTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val transparentRed = SpoolmanFilament(
        id = 5,
        name = "Polymaker PLA",
        material = "PLA",
        vendor = SpoolmanVendor(id = 1, name = "Polymaker"),
        color_hex = "FF000080",
        density = 1.24f,
        diameter = 1.75f,
        weight = 1000f,
        settings_extruder_temp = 210,
        settings_bed_temp = 60,
    )

    private fun prime(h: SpoolmanRepositoryHarness, filament: SpoolmanFilament): SpoolmanSpool {
        h.fakeApi.vendorList += filament.vendor!!
        h.fakeApi.filamentList += filament
        val spool = SpoolmanSpool(id = 7, filament = filament)
        h.fakeApi.spoolList += spool
        kotlinx.coroutines.runBlocking { h.repository.refresh() }
        h.fakeApi.callLog.clear()
        return spool
    }

    private fun patchCalls(h: SpoolmanRepositoryHarness) =
        h.fakeApi.callLog.filter { it.startsWith("patchFilament(") }

    @Test
    fun `untouched Save of a transparent spool sends no color_hex`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        val spool = prime(h, transparentRed)
        // The real form → overrides path, as a Save of an unedited spool does.
        val form = FormMapping.fromSpoolman(spool, CardUid("0a1b"), rawWriteMode = false)
        assertEquals("FF0000", form.colorHex)

        val outcome = h.repository.applyOverridesToFilamentOfSpool(7, form.toExpanderOverrides())

        assertTrue(outcome is SpoolmanOutcome.Success)
        assertTrue(patchCalls(h).all { it.contains("color_hex=null") })
        assertEquals("FF000080", h.fakeApi.filamentList.single().color_hex)
    }

    // U32 B2: the use case reports "Updated" only when this says it wrote.
    @Test
    fun `untouched Save reports wrote = false, a change reports wrote = true`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        prime(h, transparentRed)

        val untouched = h.repository.applyOverridesToFilamentOfSpool(7, ExpanderOverrides(colorHex = "FF0000", extruderTemp = 210))
        assertEquals(false, (untouched as SpoolmanOutcome.Success).data.wrote)

        val changed = h.repository.applyOverridesToFilamentOfSpool(7, ExpanderOverrides(extruderTemp = 215))
        assertEquals(true, (changed as SpoolmanOutcome.Success).data.wrote)
    }

    @Test
    fun `untouched colour with another field changed sends that field only`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        prime(h, transparentRed)

        h.repository.applyOverridesToFilamentOfSpool(
            7,
            ExpanderOverrides(colorHex = "FF0000", extruderTemp = 215),
        )

        val patch = patchCalls(h).single()
        assertTrue(patch.contains("color_hex=null"))
        assertTrue(patch.contains("settings_extruder_temp=215"))
        assertEquals("FF000080", h.fakeApi.filamentList.single().color_hex)
    }

    @Test
    fun `a new colour on a transparent filament keeps the stored alpha`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        prime(h, transparentRed)

        h.repository.applyOverridesToFilamentOfSpool(7, ExpanderOverrides(colorHex = "00FF00"))

        assertTrue(patchCalls(h).single().contains("color_hex=00FF0080"))
        assertEquals("00FF0080", h.fakeApi.filamentList.single().color_hex)
    }

    @Test
    fun `a new colour on an opaque filament is sent as plain RGB`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        prime(h, transparentRed.copy(color_hex = "FF0000"))

        h.repository.applyOverridesToFilamentOfSpool(7, ExpanderOverrides(colorHex = "00FF00"))

        assertTrue(patchCalls(h).single().contains("color_hex=00FF00,"))
    }

    @Test
    fun `new spool on an existing transparent filament leaves its colour alone`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        prime(h, transparentRed)

        val outcome = h.repository.createSpoolForExistingFilament(5, ExpanderOverrides(colorHex = "FF0000"))

        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals(0, patchCalls(h).size)
        assertEquals("FF000080", h.fakeApi.filamentList.single().color_hex)
    }
}
