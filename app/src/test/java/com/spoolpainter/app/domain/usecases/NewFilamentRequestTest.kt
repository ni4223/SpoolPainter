package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.ui.screens.main.FormState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SpoolmanDB import plumbing on NewFilamentRequest — new optional fields
 * default to null so every pre-existing call site / test keeps its behavior.
 */
class NewFilamentRequestTest {

    private val form = FormState(
        material = Material("PLA", 190, 220, 55, 65),
        colorHex = "FF0000",
        tempRanges = TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60),
    )

    @Test
    fun `fromForm defaults keep externalFilamentId and diameterMm null`() {
        val req = NewFilamentRequest.fromForm(form, name = "N", vendorName = "V")

        assertNull(req.externalFilamentId)
        assertNull(req.diameterMm)
        // Pre-existing fields unchanged.
        assertEquals("N", req.name)
        assertEquals("V", req.vendorName)
        assertEquals("PLA", req.materialName)
        assertEquals("FF0000", req.colorHex)
    }

    @Test
    fun `fromForm threads external fields through`() {
        val req = NewFilamentRequest.fromForm(
            form,
            name = "PolyLite PLA",
            vendorName = "Polymaker",
            externalFilamentId = "polymaker-polylite-pla",
            diameterMm = 2.85,
        )

        assertEquals("polymaker-polylite-pla", req.externalFilamentId)
        assertEquals(2.85, req.diameterMm!!, 0.0)
    }

    @Test
    fun `constructor defaults keep external fields null`() {
        val req = NewFilamentRequest(
            name = "N",
            vendorName = "V",
            materialName = "PLA",
            colorHex = "FF0000",
            variant = null,
            tempRanges = TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60),
        )

        assertNull(req.externalFilamentId)
        assertNull(req.diameterMm)
    }
}
