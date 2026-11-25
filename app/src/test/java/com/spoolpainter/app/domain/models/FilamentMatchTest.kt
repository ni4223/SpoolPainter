package com.spoolpainter.app.domain.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilamentMatchTest {

    private val red = SpoolmanFilament(id = 1, material = "PLA", color_hex = "FF0000")

    @Test fun `same material and colour match, case and hex format aside`() {
        assertTrue(red.isMaterialInColour("pla", "#ff0000"))
        assertTrue(red.isMaterialInColour("PLA", "FF000080"))
    }

    @Test fun `another colour does not match`() {
        assertFalse(red.isMaterialInColour("PLA", "00FF00"))
    }

    @Test fun `another material does not match`() {
        assertFalse(red.isMaterialInColour("PETG", "FF0000"))
    }

    @Test fun `two missing colours match, one missing does not`() {
        val colourless = red.copy(color_hex = null)
        assertTrue(colourless.isMaterialInColour("PLA", null))
        assertFalse(colourless.isMaterialInColour("PLA", "FF0000"))
        assertFalse(red.isMaterialInColour("PLA", null))
    }
}
