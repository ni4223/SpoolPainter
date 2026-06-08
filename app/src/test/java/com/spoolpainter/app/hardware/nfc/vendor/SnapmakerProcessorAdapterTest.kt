package com.spoolpainter.app.hardware.nfc.vendor

import com.spoolpainter.app.hardware.nfc.parseSnapmakerTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Adapter parity test (Q-U14b-5=A) for Snapmaker. Real Snapmaker tags carry
 * an RSA-2048 signature we can't forge in a unit test, so the parity check
 * is on the rejection path — both `parseSnapmakerTag` and the adapter return
 * null on a buffer that fails RSA verify (which is every fixture we can
 * construct without the upstream private key). Heavy coverage of the
 * rejection paths + offset math stays in `SnapmakerFormatTest`; an
 * end-to-end happy-path Snapmaker U1 round-trip lives in the install gate
 * scenarios.
 */
class SnapmakerProcessorAdapterTest {

    @Test fun `SnapmakerProcessor parse mirrors parseSnapmakerTag for an unsigned buffer`() {
        val data = ByteArray(1024)
        // RSA version byte @ RSA_VER_POS = 2*64 + 2*16 + 8 = 168. Leave 0 so
        // the parser picks up the v0 PEM key but verifies a zero signature.
        // Both paths return null because the signature won't validate.
        val direct = parseSnapmakerTag(data, keysA = null, keysB = null)
        val viaAdapter = SnapmakerProcessor.parse(
            uid = byteArrayOf(0x04, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66),
            raw = data,
            auth = null,
            settings = VendorSettings(),
        )
        assertNull(direct)
        assertEquals(direct, viaAdapter)
    }

    @Test fun `Snapmaker chip type matcher accepts MifareClassic`() {
        // Adapter parity for the chip-type filter: classic dispatcher used to
        // hard-code Snapmaker-as-MifareClassic; the adapter must preserve it.
        val match = SnapmakerProcessor.matchesChipType(listOf("android.nfc.tech.MifareClassic"))
        assertEquals(true, match)
    }
}
