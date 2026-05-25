package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.primitives.CardUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FormMappingTest {

    private val uid = CardUid("0a1b")

    @Test
    fun `fromSpoolman with known material in default range uses material defaults`() {
        val spool = SpoolmanSpool(
            id = 1,
            filament = SpoolmanFilament(
                id = 10,
                material = "PLA",
                vendor = SpoolmanVendor(name = "Bambu"),
                color_hex = "ff0000",
                settings_extruder_temp = 200,
                settings_bed_temp = 50,
            ),
        )
        val form = FormMapping.fromSpoolman(spool, uid, rawWriteMode = false)
        assertEquals(190, form.tempRanges.extruderMin)
        assertEquals(220, form.tempRanges.extruderMax)
        assertEquals(40, form.tempRanges.bedMin)
        assertEquals(65, form.tempRanges.bedMax)
    }

    @Test
    fun `fromSpoolman with known material out of range uses temp + 20`() {
        val spool = SpoolmanSpool(
            id = 1,
            filament = SpoolmanFilament(
                id = 10,
                material = "PLA",
                vendor = SpoolmanVendor(name = "Bambu"),
                settings_extruder_temp = 250,
                settings_bed_temp = 90,
            ),
        )
        val form = FormMapping.fromSpoolman(spool, uid, rawWriteMode = false)
        assertEquals(250, form.tempRanges.extruderMin)
        assertEquals(270, form.tempRanges.extruderMax)
        assertEquals(90, form.tempRanges.bedMin)
        assertEquals(100, form.tempRanges.bedMax)
    }

    @Test
    fun `fromSpoolman with unknown material falls back to temp + 20`() {
        val spool = SpoolmanSpool(
            id = 1,
            filament = SpoolmanFilament(
                id = 10,
                material = "Carbonite",
                settings_extruder_temp = 300,
                settings_bed_temp = 80,
            ),
        )
        val form = FormMapping.fromSpoolman(spool, uid, rawWriteMode = false)
        assertNull(form.material)
        assertEquals(300, form.tempRanges.extruderMin)
        assertEquals(320, form.tempRanges.extruderMax)
        assertEquals(80, form.tempRanges.bedMin)
        assertEquals(90, form.tempRanges.bedMax)
    }

    @Test
    fun `fromSpoolman normalises color_hex`() {
        val spool = SpoolmanSpool(
            id = 1,
            filament = SpoolmanFilament(id = 10, material = "PLA", color_hex = "#aabbcc"),
        )
        assertEquals("AABBCC", FormMapping.fromSpoolman(spool, uid, rawWriteMode = false).colorHex)
    }

    @Test
    fun `fromSpoolman with null temps yields null temps`() {
        val spool = SpoolmanSpool(
            id = 1,
            filament = SpoolmanFilament(id = 10, material = "PLA"),
        )
        val form = FormMapping.fromSpoolman(spool, uid, rawWriteMode = false)
        assertNull(form.tempRanges.extruderMin)
        assertNull(form.tempRanges.bedMax)
    }

    @Test
    fun `fromOpenSpool parses int temps`() {
        val payload = OpenSpoolPayload(
            type = "PLA",
            colorHex = "00FF00",
            brand = "Bambu",
            minTemp = "200",
            maxTemp = "215",
            bedMinTemp = "55",
            bedMaxTemp = "70",
        )
        val form = FormMapping.fromOpenSpool(uid, payload, rawWriteMode = false)
        assertEquals(200, form.tempRanges.extruderMin)
        assertEquals(215, form.tempRanges.extruderMax)
        assertEquals(55, form.tempRanges.bedMin)
        assertEquals(70, form.tempRanges.bedMax)
    }

    @Test
    fun `fromOpenSpool unparseable temps fall back to material defaults`() {
        val payload = OpenSpoolPayload(
            type = "PLA",
            colorHex = "00FF00",
            brand = "Bambu",
            minTemp = "lo",
            maxTemp = "hi",
        )
        val form = FormMapping.fromOpenSpool(uid, payload, rawWriteMode = false)
        assertEquals(190, form.tempRanges.extruderMin)
        assertEquals(220, form.tempRanges.extruderMax)
    }

    @Test
    fun `fromOpenSpool subtype Basic yields null variant`() {
        val payload = OpenSpoolPayload(
            type = "PLA",
            colorHex = null,
            brand = "Bambu",
            minTemp = "200",
            maxTemp = "215",
            subtype = "Basic",
        )
        assertNull(FormMapping.fromOpenSpool(uid, payload, rawWriteMode = false).variant)
    }

    @Test
    fun `fromOpenSpool subtype Matte yields variant`() {
        val payload = OpenSpoolPayload(
            type = "PLA",
            colorHex = null,
            brand = "Bambu",
            minTemp = "200",
            maxTemp = "215",
            subtype = "Matte",
        )
        assertEquals("Matte", FormMapping.fromOpenSpool(uid, payload, rawWriteMode = false).variant)
    }

    @Test
    fun `fromOpenSpool normalises color_hex`() {
        val payload = OpenSpoolPayload(
            type = "PLA",
            colorHex = "#aabbcc",
            brand = "Bambu",
            minTemp = "200",
            maxTemp = "215",
        )
        assertEquals("AABBCC", FormMapping.fromOpenSpool(uid, payload, rawWriteMode = false).colorHex)
    }

    @Test
    fun `fromOpenSpool with unknown type synthesises transient material`() {
        val payload = OpenSpoolPayload(
            type = "Carbonite",
            colorHex = null,
            brand = "Imperial",
            minTemp = "350",
            maxTemp = "380",
            bedMinTemp = "120",
            bedMaxTemp = "140",
        )
        val form = FormMapping.fromOpenSpool(uid, payload, rawWriteMode = false)
        assertNotNull(form.material)
        assertEquals("Carbonite", form.material?.name)
        assertEquals(350, form.tempRanges.extruderMin)
    }

    @Test
    fun `blankForm resets fields and preserves cardUid and rawWriteMode`() {
        val form = FormMapping.blankForm(uid, rawWriteMode = true)
        assertEquals(uid, form.cardUid)
        assertNull(form.material)
        assertNull(form.brand)
        assertNull(form.colorHex)
        assertNull(form.variant)
        assertEquals(true, form.rawWriteMode)
    }
}
