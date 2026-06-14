package com.spoolpainter.app.hardware.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Regression for the "Material=Polymaker, Brand=Bambu Lab" bug: a Snapmaker
 * tag (Snapmaker spools are made by Polymaker, so the chip carries the ASCII
 * "Polymaker" at the Bambu filament-type offset) was being mislabelled as a
 * Bambu tag.
 *
 * Root cause: Bambu's parser has no integrity check and returns a payload for
 * any non-blank ASCII, so when a marginal Snapmaker read failed its RSA verify
 * the dispatcher fell through to Bambu. The dispatcher fix requires a vendor
 * to have authenticated at least one sector with its OWN keys before it may
 * parse — Bambu auths nothing on a Snapmaker chip, so it can no longer win.
 *
 * These tests pin both halves of that reasoning against a real Snapmaker dump:
 *  - Bambu's parser, fed the dump, DOES produce the bogus Polymaker/Bambu Lab
 *    payload (which is exactly why it must be auth-gated out).
 *  - Snapmaker's parser produces the correct payload.
 */
class SnapmakerNotBambuRegressionTest {

    private val snapmakerBlack: ByteArray =
        javaClass.getResourceAsStream("/vendor-fixtures/Snapmaker/Snapmaker SnapSpeed PLA Black 500g.bin")!!
            .readBytes()

    @Test
    fun `bambu parser misreads a snapmaker chip as Polymaker - the reason auth-gating is required`() {
        val asBambu = parseBambuTag(snapmakerBlack)
        assertNotNull("Bambu parser has no integrity check, so it returns a payload", asBambu)
        // This is the WRONG output the user saw. It only reached the form
        // because the old dispatcher let Bambu parse a chip it never unlocked.
        assertEquals("Polymaker", asBambu!!.type)
        assertEquals("Bambu Lab", asBambu.brand)
    }

    @Test
    fun `snapmaker parser reads the same chip correctly`() {
        val asSnapmaker = parseSnapmakerTag(snapmakerBlack, keysA = null, keysB = null)
        assertNotNull(asSnapmaker)
        assertEquals("PLA", asSnapmaker!!.type)
        assertEquals("Snapmaker", asSnapmaker.brand)
        assertEquals("SnapSpeed", asSnapmaker.subtype)
        assertEquals("080A0D", asSnapmaker.colorHex)
        assertNotEquals("must never surface the Bambu mislabel", "Bambu Lab", asSnapmaker.brand)
    }
}
