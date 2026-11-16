package com.spoolpainter.app.data.remote.spoolman

import com.google.gson.Gson
import com.spoolpainter.app.domain.models.ExternalFilament
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SpoolmanDB wire contract — GET /api/v1/external/filament/search items are
 * snake_case JSON with response_model_exclude_none semantics (unknown/empty
 * fields are absent, not null). The pinned domain contract is camelCase, so
 * these tests pin the Gson mapping.
 */
class ExternalFilamentDtoTest {

    private val gson = Gson()

    @Test
    fun `full snake_case payload maps every field`() {
        val json = """
            [{
              "id": "polymaker-polylite-pla",
              "manufacturer": "Polymaker",
              "name": "PolyLite PLA",
              "material": "PLA",
              "density": 1.27,
              "weight": 1000,
              "spool_weight": 149,
              "spool_type": "plastic",
              "diameter": 1.75,
              "color_hex": "E1C69E",
              "color_hexes": ["2c3232", "5f5f5f"],
              "extruder_temp": 210,
              "bed_temp": 60,
              "finish": "matte",
              "pattern": "marble",
              "translucent": false,
              "glow": false,
              "multi_color_direction": "coaxial"
            }]
        """.trimIndent()

        val parsed = gson.fromJson(json, Array<ExternalFilament>::class.java).single()

        assertEquals("polymaker-polylite-pla", parsed.id)
        assertEquals("Polymaker", parsed.manufacturer)
        assertEquals("PolyLite PLA", parsed.name)
        assertEquals("PLA", parsed.material)
        assertEquals(1.27, parsed.density!!, 1e-9)
        assertEquals(1000.0, parsed.weight!!, 1e-9)
        assertEquals(149.0, parsed.spoolWeight!!, 1e-9)
        assertEquals("plastic", parsed.spoolType)
        assertEquals(1.75, parsed.diameter!!, 1e-9)
        assertEquals("E1C69E", parsed.colorHex)
        assertEquals(listOf("2c3232", "5f5f5f"), parsed.colorHexes)
        assertEquals(210, parsed.extruderTemp)
        assertEquals(60, parsed.bedTemp)
        assertEquals("matte", parsed.finish)
        assertEquals("marble", parsed.pattern)
        assertEquals(false, parsed.translucent)
        assertFalse(parsed.translucent!!)
        assertFalse(parsed.glow!!)
        assertEquals("coaxial", parsed.multiColorDirection)
    }

    @Test
    fun `sparse payload maps absent optional fields to null`() {
        val json = """
            {"id":"bambu-apa","manufacturer":"Bambu Lab","name":"PLA Aqua","material":"PLA"}
        """.trimIndent()

        val parsed = gson.fromJson(json, ExternalFilament::class.java)

        assertEquals("bambu-apa", parsed.id)
        assertEquals("Bambu Lab", parsed.manufacturer)
        assertEquals("PLA Aqua", parsed.name)
        assertEquals("PLA", parsed.material)
        assertNull(parsed.density)
        assertNull(parsed.weight)
        assertNull(parsed.spoolWeight)
        assertNull(parsed.spoolType)
        assertNull(parsed.diameter)
        assertNull(parsed.colorHex)
        assertNull(parsed.colorHexes)
        assertNull(parsed.extruderTemp)
        assertNull(parsed.bedTemp)
        assertNull(parsed.finish)
        assertNull(parsed.pattern)
        assertNull(parsed.translucent)
        assertNull(parsed.glow)
        assertNull(parsed.multiColorDirection)
    }

    @Test
    fun `single-color payload carries color_hex and no color_hexes`() {
        val json = """
            {"id":"x","manufacturer":"M","name":"N","material":"PLA","color_hex":"E1C69E"}
        """.trimIndent()

        val parsed = gson.fromJson(json, ExternalFilament::class.java)

        assertEquals("E1C69E", parsed.colorHex)
        assertNull(parsed.colorHexes)
    }

    @Test
    fun `create filament POST body serialises external_id and omits it when null`() {
        val base = CreateFilamentRequest(
            name = null,
            vendor_id = 7,
            material = "PLA",
            color_hex = "FF0000",
            settings_extruder_temp = null,
            settings_bed_temp = null,
            density = 1.24f,
            diameter = 1.75f,
            weight = 1000f,
        )
        assertTrue(gson.toJson(base.copy(external_id = "slug")).contains("\"external_id\":\"slug\""))
        assertTrue(!gson.toJson(base).contains("external_id"))
    }

    @Test
    fun `create vendor POST body serialises external_id and omits it when null`() {
        val base = CreateVendorRequest(name = "Polymaker")
        assertTrue(gson.toJson(base.copy(external_id = "Polymaker")).contains("\"external_id\":\"Polymaker\""))
        assertTrue(!gson.toJson(base).contains("external_id"))
    }
}
