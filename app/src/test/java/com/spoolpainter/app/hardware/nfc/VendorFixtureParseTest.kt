package com.spoolpainter.app.hardware.nfc

import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.hardware.nfc.vendor.AnycubicProcessor
import com.spoolpainter.app.hardware.nfc.vendor.ElegooProcessor
import com.spoolpainter.app.hardware.nfc.vendor.QidiProcessor
import com.spoolpainter.app.hardware.nfc.vendor.VendorSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.Test

/**
 * Byte-accurate regression coverage driven by real OpenRFID chip dumps (see
 * [VendorFixtureSupport] for provenance). Each fixture's `.bin` is parsed by
 * the production processor and asserted against its `.yml` expected output.
 *
 * This is the JVM-side coverage gap that let the U14b vendor-decode bugs ship:
 * Elegoo / Anycubic chips reading as Blank, and a marginal Snapmaker tap being
 * mislabelled as Bambu. Hardware-free, so it runs in CI on every change.
 *
 * Creality is excluded — its fixtures need a per-tag AES encryption key that
 * upstream keeps in an env var, not in the repo, so we can't decrypt them.
 *
 * The UID is irrelevant to every parser exercised here: Elegoo / Anycubic /
 * QIDI don't derive from it, and Snapmaker dumps carry their real sector-key
 * trailers so we pass `null` keys (skipping our derived-key reconstruction)
 * and verify the RSA signature against the dump bytes as-is.
 */
@RunWith(Parameterized::class)
class VendorFixtureParseTest(private val fixture: VendorFixtureSupport.Fixture) {

    @Test
    fun `parses to expected output`() {
        val e = fixture.expected
        val uid = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val settings = VendorSettings()

        val payload: OpenSpoolPayload? = when (fixture.vendorDir) {
            "Elegoo" -> ElegooProcessor.parse(uid, fixture.bin, auth = null, settings = settings)
            "Anycubic" -> AnycubicProcessor.parse(uid, fixture.bin, auth = null, settings = settings)
            "Qidi" -> QidiProcessor.parse(uid, fixture.bin, auth = null, settings = settings)
            "Bambu" -> parseBambuTag(fixture.bin)
            "Snapmaker" -> parseSnapmakerTag(fixture.bin, keysA = null, keysB = null)
            else -> error("no parser wired for ${fixture.vendorDir}")
        }

        assertNotNull("parse returned null for $fixture", payload)
        payload!!

        assertEquals("type", e.type, payload.type)
        assertEquals("brand", expectedBrand(fixture.vendorDir, e.manufacturer), payload.brand)
        assertEquals("colorHex", e.colorRgbHex, payload.colorHex)
        assertEquals("minTemp", e.hotendMinC.toString(), payload.minTemp)
        assertEquals("maxTemp", e.hotendMaxC.toString(), payload.maxTemp)
        assertEquals("subtype", expectedSubtype(e.modifiers), payload.subtype)
    }

    companion object {
        /**
         * Our `brand` mapping differs from upstream `manufacturer` for two
         * vendors: Anycubic chips encode "AC" (we normalise to "Anycubic"),
         * and Bambu's parser hardcodes "Bambu Lab" (upstream says "Bambu").
         */
        private fun expectedBrand(vendorDir: String, manufacturer: String): String = when (vendorDir) {
            "Anycubic" -> "Anycubic"
            "Bambu" -> "Bambu Lab"
            else -> manufacturer
        }

        /** Processors fold the first modifier into `subtype`, defaulting to "Basic". */
        private fun expectedSubtype(modifiers: List<String>): String =
            modifiers.firstOrNull() ?: "Basic"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<VendorFixtureSupport.Fixture> =
            listOf("Elegoo", "Anycubic", "Qidi", "Snapmaker", "Bambu")
                .flatMap { VendorFixtureSupport.load(it) }
    }
}
