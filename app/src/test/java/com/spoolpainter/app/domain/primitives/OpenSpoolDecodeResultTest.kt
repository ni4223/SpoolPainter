package com.spoolpainter.app.domain.primitives

import com.spoolpainter.app.domain.models.OpenSpoolPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSpoolDecodeResultTest {

    private val samplePayload = OpenSpoolPayload(
        type = "PLA",
        colorHex = "ff0000",
        brand = "Generic",
        minTemp = "190",
        maxTemp = "220",
    )

    @Test
    fun `Success equality on equal payload`() {
        val a: OpenSpoolDecodeResult = OpenSpoolDecodeResult.Success(samplePayload)
        val b: OpenSpoolDecodeResult = OpenSpoolDecodeResult.Success(samplePayload.copy())
        assertEquals(a, b)
    }

    @Test
    fun `Malformed equality on equal reason`() {
        val a: OpenSpoolDecodeResult = OpenSpoolDecodeResult.Malformed("missing min_temp")
        val b: OpenSpoolDecodeResult = OpenSpoolDecodeResult.Malformed("missing min_temp")
        assertEquals(a, b)
    }

    @Test
    fun `Malformed inequality on different reason`() {
        val a: OpenSpoolDecodeResult = OpenSpoolDecodeResult.Malformed("a")
        val b: OpenSpoolDecodeResult = OpenSpoolDecodeResult.Malformed("b")
        assertNotEquals(a, b)
    }

    @Test
    fun `NotOpenSpool is a singleton`() {
        val a: OpenSpoolDecodeResult = OpenSpoolDecodeResult.NotOpenSpool
        val b: OpenSpoolDecodeResult = OpenSpoolDecodeResult.NotOpenSpool
        assertSame(a, b)
    }

    @Test
    fun `Success and Malformed are not equal`() {
        val a: OpenSpoolDecodeResult = OpenSpoolDecodeResult.Success(samplePayload)
        val b: OpenSpoolDecodeResult = OpenSpoolDecodeResult.Malformed("anything")
        assertNotEquals(a, b)
    }

    @Test
    fun `sealed exhaustiveness compiles without else branch`() {
        val r: OpenSpoolDecodeResult = OpenSpoolDecodeResult.NotOpenSpool
        val handled = when (r) {
            is OpenSpoolDecodeResult.Success -> "success"
            is OpenSpoolDecodeResult.Malformed -> "malformed"
            OpenSpoolDecodeResult.NotOpenSpool -> "not"
        }
        assertTrue(handled == "not")
    }
}
