package com.spoolpainter.app.hardware.nfc.vendor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Creality MifareClassic tag parser tests. Uses real AES-256-ECB to build
 * the encrypted fixture (so the parser's decrypt path is genuinely
 * exercised, not stubbed). Plaintext fixtures use the marker bytes the
 * parser checks (data[3]=0x32 AND data[17] ∈ {0x30, 0x23}).
 */
class CrealityProcessorTest {

    private val uid = byteArrayOf(0x04, 0x11, 0x22, 0x33)

    /** Build a 48-byte ASCII payload with a marker at byte 17 = '0' (0x30) so it's plaintext. */
    private fun plainPayload(material: String = "01001"): ByteArray {
        // batch(3) + date5 + supplier4 + material5 + color7 (prefix + 6 hex) + length4 + serial6 + reserve14 = 48.
        val batch = "B01"
        val date = "25" + "5" + "12" // yy + month-hex + dd → 5 chars
        val supplier = "SUP1"
        val color = "0FF00FF" // prefix '0' + RRGGBB
        val length = "0330"
        val serial = "SN0001"
        val reserve = "RR".padEnd(14, 'X')
        val s = batch + date + supplier + material + color + length + serial + reserve
        require(s.length == 48) { "fixture length ${s.length}" }
        // Force markers so parser detects plaintext: byte 3 must be 0x32 ('2') and byte 17 must be 0x30 ('0').
        val bytes = s.toByteArray(Charsets.US_ASCII)
        // Byte 3 in our string is the start of date "25...", first char '2' = 0x32 ✓.
        // Byte 17 is the color prefix '0' = 0x30 ✓.
        return bytes
    }

    private fun makeRaw(payload: ByteArray): ByteArray {
        val raw = ByteArray(1024)
        payload.copyInto(raw, destinationOffset = 64)
        return raw
    }

    private fun aesEcbEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(plaintext)
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    @Test fun `plaintext tag with HKDF salt set parses cleanly`() {
        val raw = makeRaw(plainPayload("01001"))
        val settings = VendorSettings(crealitySalt = hex(ByteArray(32) { 0x11 }))
        val out = CrealityProcessor.parse(uid, raw, auth = null, settings = settings)
        assertNotNull(out)
        assertEquals("PLA", out!!.type)
        assertEquals("Creality", out.brand)
        assertEquals("190", out.minTemp)
        assertEquals("240", out.maxTemp)
        assertEquals("FF00FF", out.colorHex)
        assertEquals("Hyper", out.subtype)
    }

    @Test fun `encrypted tag with both keys configured decrypts and parses`() {
        val encKey = ByteArray(32) { (it * 7 + 1).toByte() }
        val plain = plainPayload("06002") // PETG Hyper
        val encrypted = aesEcbEncrypt(encKey, plain)
        val raw = makeRaw(encrypted)
        val settings = VendorSettings(
            crealitySalt = hex(ByteArray(32) { 0x11 }),
            crealityEncKey = hex(encKey),
        )
        val out = CrealityProcessor.parse(uid, raw, auth = null, settings = settings)
        assertNotNull(out)
        assertEquals("PETG", out!!.type)
        assertEquals("220", out.minTemp)
        assertEquals("270", out.maxTemp)
    }

    @Test fun `encrypted tag with no enc key returns null silently (Q-U14b-4=A)`() {
        val encKey = ByteArray(32) { 0x42 }
        val plain = plainPayload("01001")
        val encrypted = aesEcbEncrypt(encKey, plain)
        val raw = makeRaw(encrypted)
        val settings = VendorSettings(crealitySalt = hex(ByteArray(32) { 0x11 }))
        // No enc key configured.
        val out = CrealityProcessor.parse(uid, raw, auth = null, settings = settings)
        assertNull(out)
    }

    @Test fun `unknown material code rejected`() {
        // Replace material slice with code not in the table.
        val payload = plainPayload("01001")
        val unknown = "ZZZZZ".toByteArray(Charsets.US_ASCII)
        unknown.copyInto(payload, destinationOffset = 12)
        val raw = makeRaw(payload)
        val settings = VendorSettings(crealitySalt = hex(ByteArray(32) { 0x11 }))
        assertNull(CrealityProcessor.parse(uid, raw, auth = null, settings = settings))
    }

    @Test fun `short raw bytes rejected`() {
        val settings = VendorSettings(crealitySalt = hex(ByteArray(32) { 0x11 }))
        assertNull(CrealityProcessor.parse(uid, ByteArray(50), auth = null, settings = settings))
    }

    @Test fun `deriveAuthKeys with 32-byte salt produces sector-1 derived key`() {
        val salt = ByteArray(32) { 0x33 }
        val auth = CrealityProcessor.deriveAuthKeys(uid, VendorSettings(crealitySalt = hex(salt)))
        assertNotNull(auth)
        // sector-1 key A is the AES-encrypt of (uid * 4) under saltKey, first 6 bytes.
        val plaintext = ByteArray(16)
        for (i in 0..3) uid.copyInto(plaintext, destinationOffset = i * 4)
        val expected = aesEcbEncrypt(salt, plaintext).copyOfRange(0, 6)
        // Sector 0 stays default 0xFF*6, sector 1 == derived.
        for (i in 0 until 6) {
            assertEquals(0xFF.toByte(), auth!!.keysA[0][i])
        }
        for (i in 0 until 6) {
            assertEquals(expected[i], auth!!.keysA[1][i])
        }
    }

    @Test fun `deriveAuthKeys returns null when salt is blank`() {
        val auth = CrealityProcessor.deriveAuthKeys(uid, VendorSettings(crealitySalt = ""))
        assertNull(auth)
    }

    @Test fun `isEnabled reflects salt presence`() {
        assertEquals(false, CrealityProcessor.isEnabled(VendorSettings()))
        assertEquals(true, CrealityProcessor.isEnabled(VendorSettings(crealitySalt = "AABB")))
    }
}
