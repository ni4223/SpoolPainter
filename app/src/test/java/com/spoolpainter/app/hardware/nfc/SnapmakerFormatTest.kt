package com.spoolpainter.app.hardware.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function tests for the Snapmaker binary tag parser.
 *
 * `parseSnapmakerTag` runs an RSA-signature verify before extracting fields.
 * We don't ship a fixture with a valid Snapmaker signature (and forging one
 * isn't the point of these tests), so the parse-success path is covered
 * end-to-end by the install-gate Snapmaker U1 round-trip in
 * `aidlc-docs/operations/manual-nfc-checklist.md`. Unit tests here cover:
 *
 *   - HKDF derivation determinism + 16-key shape
 *   - parser rejects buffers whose size != 1024
 *   - parser rejects unsigned / hand-crafted buffers (verify must fail)
 */
class SnapmakerFormatTest {

    @Test
    fun `parseSnapmakerTag rejects buffers whose size is not 1024`() {
        assertNull(parseSnapmakerTag(ByteArray(0)))
        assertNull(parseSnapmakerTag(ByteArray(1023)))
        assertNull(parseSnapmakerTag(ByteArray(1025)))
    }

    @Test
    fun `parseSnapmakerTag returns null when signature verify fails`() {
        // A 1024-byte buffer of zeros has rsaVer=0 (matches a real key
        // index), but the embedded signature bytes are zero — verify fails.
        val data = ByteArray(1024)
        assertNull(parseSnapmakerTag(data, keysA = null, keysB = null))
    }

    @Test
    fun `snapmakerDeriveKeys returns 16 keysA and 16 keysB of 6 bytes each`() {
        val uid4 = byteArrayOf(0x04, 0x12, 0x34, 0x56)
        // Salt must be at least 25 hex chars (50 hex digits) — the Snapmaker
        // codebase splits saltA = first 25 bytes, saltB = full salt.
        val salt = "536e61706d616b65725f71776572747975696f705b2c2e3b5d5f317132773365"
        val (keysA, keysB) = snapmakerDeriveKeys(uid4, salt)
        assertEquals(16, keysA.size)
        assertEquals(16, keysB.size)
        keysA.forEach { assertEquals(6, it.size) }
        keysB.forEach { assertEquals(6, it.size) }
    }

    @Test
    fun `snapmakerDeriveKeys is deterministic`() {
        val uid4 = byteArrayOf(0x04, 0x12, 0x34, 0x56)
        val salt = "536e61706d616b65725f71776572747975696f705b2c2e3b5d5f317132773365"
        val (a1, b1) = snapmakerDeriveKeys(uid4, salt)
        val (a2, b2) = snapmakerDeriveKeys(uid4, salt)
        a1.zip(a2).forEach { (x, y) -> assertTrue(x.contentEquals(y)) }
        b1.zip(b2).forEach { (x, y) -> assertTrue(x.contentEquals(y)) }
    }

    @Test
    fun `snapmakerDeriveKeys keysA and keysB diverge for the same UID`() {
        val uid4 = byteArrayOf(0x04, 0x12, 0x34, 0x56)
        val salt = "536e61706d616b65725f71776572747975696f705b2c2e3b5d5f317132773365"
        val (keysA, keysB) = snapmakerDeriveKeys(uid4, salt)
        // saltA = salt[0..25], saltB = full salt → "key_a_*" vs "key_b_*"
        // info string. Sector 0 keys must NOT match.
        assertTrue(!keysA[0].contentEquals(keysB[0]))
    }
}
