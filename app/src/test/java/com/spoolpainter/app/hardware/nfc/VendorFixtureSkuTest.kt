package com.spoolpainter.app.hardware.nfc

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Product code ([com.spoolpainter.app.domain.models.OpenSpoolPayload.sku])
 * decoded from the real chip dumps in `vendor-fixtures/`. Expected values
 * were read straight off the `.bin` files at each format's documented offset.
 */
class VendorFixtureSkuTest {

    private fun skus(vendorDir: String): Map<String, String?> =
        VendorFixtureSupport.load(vendorDir).associate { fixture ->
            fixture.name to VendorFixtureSupport.parse(fixture)!!.sku
        }

    @Test
    fun `Anycubic - SKU string`() {
        assertEquals(mapOf("Anycubic PLA Red" to "AHPLRR-107"), skus("Anycubic"))
    }

    @Test
    fun `Bambu - material variant ID`() {
        assertEquals(
            mapOf(
                "PETG-Basic-Red" to "G00-R0",
                "PETG-HF-Green" to "G02-G0",
                "PETG-Translucent-Orange" to "G02-A0",
                "PLA-Basic-Bronze" to "A00-Y3",
                "PLA-CF-Black" to "A50-K0",
            ),
            skus("Bambu"),
        )
    }

    @Test
    fun `Snapmaker - numeric SKU`() {
        assertEquals(
            mapOf(
                "Snapmaker SnapSpeed PLA Black 500g" to "900001",
                "Snapmaker SnapSpeed PLA Red 500g" to "900002",
                "Snapmaker SnapSpeed PLA White 500g" to "900000",
                "Snapmaker SnapSpeed PLA Yellow 500g" to "900003",
            ),
            skus("Snapmaker"),
        )
    }

    @Test
    fun `vendors without a product code on the tag report none`() {
        (skus("Elegoo") + skus("Qidi")).forEach { (name, sku) ->
            assertEquals("sku for $name", null, sku)
        }
    }
}
