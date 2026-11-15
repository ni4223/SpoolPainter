package com.spoolpainter.app.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** OpenSpoolPayload.productCode — only printable codes reach Spoolman. */
class ProductCodeTest {

    private fun payload(sku: String?) =
        OpenSpoolPayload(type = "PLA", colorHex = "FFFFFF", brand = "Anycubic", minTemp = "190", maxTemp = "230", sku = sku)

    @Test
    fun `printable code passes, trimmed`() {
        assertEquals("HPL18-106", payload(" HPL18-106 ").productCode())
        assertEquals("A00 R1", payload("A00 R1").productCode())
    }

    @Test
    fun `erased or garbled bytes are no code`() {
        assertNull(payload("����").productCode())
        assertNull(payload("HPL\u0001\u0002").productCode())
        assertNull(payload("   ").productCode())
        assertNull(payload(null).productCode())
    }
}
