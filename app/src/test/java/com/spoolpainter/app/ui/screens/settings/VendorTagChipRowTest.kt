package com.spoolpainter.app.ui.screens.settings

import com.spoolpainter.app.hardware.nfc.vendor.VendorId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vendor row spec generator. Drives ready/missing-key state from the three
 * key settings + the always-ready vendors. Run on JVM (no Compose test infra).
 */
class VendorTagChipRowTest {

    @Test fun `row count is seven and ordering is alphabetical`() {
        val rows = vendorRowSpecs(bambuSalt = "", crealitySalt = "", crealityEncKey = "")
        assertEquals(7, rows.size)
        assertEquals(
            listOf("Anycubic", "Bambu Lab", "Creality", "Elegoo", "OpenSpool", "QIDI", "Snapmaker"),
            rows.map { it.label },
        )
    }

    @Test fun `Bambu row is missing-key when salt is empty`() {
        val rows = vendorRowSpecs(bambuSalt = "", crealitySalt = "", crealityEncKey = "")
        val bambu = rows.first { it.id == VendorId.Bambu }
        assertFalse(bambu.ready)
        assertTrue(bambu.keyable)
    }

    @Test fun `Bambu row is ready when salt is set`() {
        val rows = vendorRowSpecs(bambuSalt = "DEADBEEF", crealitySalt = "", crealityEncKey = "")
        val bambu = rows.first { it.id == VendorId.Bambu }
        assertTrue(bambu.ready)
    }

    @Test fun `Creality row is missing-key when HKDF salt is empty`() {
        val rows = vendorRowSpecs(bambuSalt = "", crealitySalt = "", crealityEncKey = "")
        val creality = rows.first { it.id == VendorId.Creality }
        assertFalse(creality.ready)
        assertTrue(creality.keyable)
    }

    @Test fun `Creality row is ready with HKDF salt set even without enc key`() {
        // Plaintext Creality tags work with just the salt — see §1.3 of the
        // U14b plan and Q-U14b-4=A (encrypted-no-key path is silent log).
        val rows = vendorRowSpecs(bambuSalt = "", crealitySalt = "ABCD", crealityEncKey = "")
        val creality = rows.first { it.id == VendorId.Creality }
        assertTrue(creality.ready)
    }

    @Test fun `OpenSpool Snapmaker QIDI Anycubic Elegoo are always ready and not keyable`() {
        val rows = vendorRowSpecs(bambuSalt = "", crealitySalt = "", crealityEncKey = "")
        val alwaysReady = setOf(VendorId.OpenSpool, VendorId.Snapmaker, VendorId.Qidi, VendorId.Anycubic, VendorId.Elegoo)
        for (row in rows) {
            if (row.id in alwaysReady) {
                assertTrue("${row.id} should be ready", row.ready)
                assertFalse("${row.id} should not be keyable", row.keyable)
            }
        }
    }
}
