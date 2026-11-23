package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.TempRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** U32 B1/B2: Save says why it's disabled; an existing spool may have no color (D1b). */
class FormSaveBlockerTest {

    private val valid = FormState(
        material = Material("PLA", 190, 220, 40, 65, density = 1.24f),
        brand = Brand("Polymaker"),
        colorHex = "FF0000",
        tempRanges = TempRanges(extruderMin = 190, extruderMax = 220, bedMin = 40, bedMax = 65),
    )

    @Test
    fun `a valid form has no blocker`() {
        assertNull(valid.saveBlocker)
        assertTrue(valid.canSubmit)
    }

    @Test
    fun `existing spool with no color can save but not write`() {
        val form = valid.copy(selectedSpoolId = 7, colorHex = null)
        assertNull(form.saveBlocker)
        assertFalse(form.canSubmit)
    }

    @Test
    fun `new spool with no color is blocked with a reason`() {
        assertEquals("Pick a color.", valid.copy(colorHex = null).saveBlocker)
    }

    @Test
    fun `invalid color is blocked even on an existing spool`() {
        assertEquals("Color must be 6 hex digits.", valid.copy(selectedSpoolId = 7, colorHex = "12345").saveBlocker)
    }

    @Test
    fun `missing nozzle temps say where to fix them`() {
        val form = valid.copy(tempRanges = TempRanges(extruderMin = null, extruderMax = null, bedMin = null, bedMax = null))
        assertEquals("Set the Nozzle temperature in More details.", form.saveBlocker)
    }

    @Test
    fun `missing material and brand are reported first`() {
        assertEquals("Pick a material.", valid.copy(material = null).saveBlocker)
        assertEquals("Pick a brand.", valid.copy(brand = null).saveBlocker)
    }

    @Test
    fun `Measured entry with no empty spool weight blocks Save`() {
        val form = valid.copy(
            selectedSpoolId = 7,
            weightMethod = WeightMethod.Measured,
            measuredEntry = 1250f,
            emptySpoolWeightG = null,
        )
        assertEquals("Enter the empty spool weight to use Measured.", form.saveBlocker)
    }

    @Test
    fun `Measured with an empty spool weight or no entry is fine`() {
        val base = valid.copy(selectedSpoolId = 7, weightMethod = WeightMethod.Measured)
        assertNull(base.copy(measuredEntry = 1250f, emptySpoolWeightG = 200f).saveBlocker)
        assertNull(base.copy(measuredEntry = null, emptySpoolWeightG = null).saveBlocker)
    }
}
