package com.spoolpainter.app.domain.primitives

import com.spoolpainter.app.domain.models.OpenSpoolPayload
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSpoolPayloadCodecTest {

    private val fullPayload = OpenSpoolPayload(
        protocol = "openspool",
        version = "1.0",
        type = "PLA",
        colorHex = "ff0000",
        brand = "Generic",
        minTemp = "190",
        maxTemp = "220",
        bedMinTemp = "40",
        bedMaxTemp = "65",
        subtype = "Matte",
        spoolId = "42",
        lotNr = null,
    )

    private val minimalPayload = OpenSpoolPayload(
        type = "PLA",
        colorHex = null,
        brand = "Generic",
        minTemp = "190",
        maxTemp = "220",
    )

    @Test
    fun `round-trip with full payload`() {
        val json = OpenSpoolPayloadCodec.toJson(fullPayload)
        val result = OpenSpoolPayloadCodec.fromJson(json)
        assertEquals(OpenSpoolDecodeResult.Success(fullPayload), result)
    }

    @Test
    fun `round-trip with minimal payload — colorHex stays null`() {
        val json = OpenSpoolPayloadCodec.toJson(minimalPayload)
        val result = OpenSpoolPayloadCodec.fromJson(json)
        assertEquals(OpenSpoolDecodeResult.Success(minimalPayload), result)
    }

    @Test
    fun `missing type yields Malformed`() {
        val json = """{"protocol":"openspool","brand":"Generic","min_temp":"190","max_temp":"220"}"""
        assertEquals(
            OpenSpoolDecodeResult.Malformed("missing type"),
            OpenSpoolPayloadCodec.fromJson(json),
        )
    }

    @Test
    fun `missing brand yields Malformed`() {
        val json = """{"protocol":"openspool","type":"PLA","min_temp":"190","max_temp":"220"}"""
        assertEquals(
            OpenSpoolDecodeResult.Malformed("missing brand"),
            OpenSpoolPayloadCodec.fromJson(json),
        )
    }

    @Test
    fun `missing min_temp yields Malformed`() {
        val json = """{"protocol":"openspool","type":"PLA","brand":"Generic","max_temp":"220"}"""
        assertEquals(
            OpenSpoolDecodeResult.Malformed("missing min_temp"),
            OpenSpoolPayloadCodec.fromJson(json),
        )
    }

    @Test
    fun `missing max_temp yields Malformed`() {
        val json = """{"protocol":"openspool","type":"PLA","brand":"Generic","min_temp":"190"}"""
        assertEquals(
            OpenSpoolDecodeResult.Malformed("missing max_temp"),
            OpenSpoolPayloadCodec.fromJson(json),
        )
    }

    @Test
    fun `non-openspool protocol yields NotOpenSpool`() {
        val json = """{"protocol":"foo","type":"PLA","brand":"Generic","min_temp":"190","max_temp":"220"}"""
        assertEquals(OpenSpoolDecodeResult.NotOpenSpool, OpenSpoolPayloadCodec.fromJson(json))
    }

    @Test
    fun `missing protocol field yields NotOpenSpool`() {
        val json = """{"type":"PLA","brand":"Generic","min_temp":"190","max_temp":"220"}"""
        assertEquals(OpenSpoolDecodeResult.NotOpenSpool, OpenSpoolPayloadCodec.fromJson(json))
    }

    @Test
    fun `invalid JSON yields NotOpenSpool — not a json string`() {
        assertEquals(OpenSpoolDecodeResult.NotOpenSpool, OpenSpoolPayloadCodec.fromJson("not a json"))
    }

    @Test
    fun `invalid JSON yields NotOpenSpool — unterminated`() {
        assertEquals(OpenSpoolDecodeResult.NotOpenSpool, OpenSpoolPayloadCodec.fromJson("{"))
    }

    @Test
    fun `empty input yields NotOpenSpool`() {
        assertEquals(OpenSpoolDecodeResult.NotOpenSpool, OpenSpoolPayloadCodec.fromJson(""))
    }

    @Test
    fun `JSON with leading non-brace prefix is tolerated`() {
        val json = """en{"protocol":"openspool","type":"PLA","color_hex":"ff0000","brand":"Generic","min_temp":"190","max_temp":"220","subtype":"Basic"}"""
        val result = OpenSpoolPayloadCodec.fromJson(json)
        assertTrue(result is OpenSpoolDecodeResult.Success)
        val payload = (result as OpenSpoolDecodeResult.Success).payload
        assertEquals("PLA", payload.type)
        assertEquals("Generic", payload.brand)
    }

    @Test
    fun `lot_nr present in JSON populates payload-lotNr on decode`() {
        val json = """{"protocol":"openspool","type":"PLA","color_hex":"","brand":"Generic","min_temp":"190","max_temp":"220","subtype":"Basic","lot_nr":"ABC123"}"""
        val result = OpenSpoolPayloadCodec.fromJson(json)
        assertTrue(result is OpenSpoolDecodeResult.Success)
        val payload = (result as OpenSpoolDecodeResult.Success).payload
        assertEquals("ABC123", payload.lotNr)
    }

    @Test
    fun `payload with non-null lotNr is encoded WITHOUT lot_nr field`() {
        val payload = fullPayload.copy(lotNr = "ABC123")
        val json = OpenSpoolPayloadCodec.toJson(payload)
        val obj = JSONObject(json)
        assertFalse("lot_nr field MUST NOT be emitted on encode", obj.has("lot_nr"))
    }

    @Test
    fun `encode-decode of payload with lotNr sanitises lotNr to null`() {
        val payload = fullPayload.copy(lotNr = "ABC123")
        val json = OpenSpoolPayloadCodec.toJson(payload)
        val result = OpenSpoolPayloadCodec.fromJson(json)
        assertTrue(result is OpenSpoolDecodeResult.Success)
        val decoded = (result as OpenSpoolDecodeResult.Success).payload
        assertNull(decoded.lotNr)
        assertEquals(payload.copy(lotNr = null), decoded)
    }

    @Test
    fun `unknown JSON fields are silently ignored on decode`() {
        val json = """{"protocol":"openspool","type":"PLA","color_hex":"","brand":"Generic","min_temp":"190","max_temp":"220","subtype":"Basic","vendor_extra":"xyz","another_field":42}"""
        val result = OpenSpoolPayloadCodec.fromJson(json)
        assertTrue(result is OpenSpoolDecodeResult.Success)
    }

    @Test
    fun `colorHex emitted as empty string when null on encode (v1 quirk)`() {
        val json = OpenSpoolPayloadCodec.toJson(minimalPayload)
        val obj = JSONObject(json)
        assertTrue(obj.has("color_hex"))
        assertEquals("", obj.optString("color_hex"))
    }
}
