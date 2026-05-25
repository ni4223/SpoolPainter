package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.primitives.CardUid
import org.junit.Assert.assertEquals
import org.junit.Test

class CardUidEncodingRoundTripTest {

    @Test
    fun `decode-encode round-trip preserves UID list for canonical input`() {
        val uids = listOf(CardUid("aabb"), CardUid("ccdd"))
        val opaque = ""
        val encoded = CardUidEncoding.encode(uids, opaque)
        val decoded = CardUidEncoding.decode(encoded)
        assertEquals(uids, decoded.uids)
        assertEquals(opaque, decoded.opaque)
    }

    @Test
    fun `decode-encode round-trip preserves opaque for canonical input`() {
        val uids = listOf(CardUid("aabb"))
        val opaque = "batch=42,notes=foo"
        val encoded = CardUidEncoding.encode(uids, opaque)
        val decoded = CardUidEncoding.decode(encoded)
        assertEquals(uids, decoded.uids)
        assertEquals(opaque, decoded.opaque)
    }

    @Test
    fun `mixed-case input collapses to canonical lowercase after one round-trip`() {
        val firstPass = CardUidEncoding.decode("CARD_UID:AABB")
        val firstEncoded = CardUidEncoding.encode(firstPass.uids, firstPass.opaque)
        assertEquals("card_uid:aabb", firstEncoded)

        // Second pass — fixed point.
        val secondPass = CardUidEncoding.decode(firstEncoded)
        assertEquals(firstPass.uids, secondPass.uids)
        assertEquals(firstPass.opaque, secondPass.opaque)
        assertEquals(firstEncoded, CardUidEncoding.encode(secondPass.uids, secondPass.opaque))
    }

    @Test
    fun `interleaved opaque entries collapse to tail block after one round-trip`() {
        val original = "card_uid:aa,batch=42,card_uid:bb,notes=foo"
        val firstPass = CardUidEncoding.decode(original)
        val firstEncoded = CardUidEncoding.encode(firstPass.uids, firstPass.opaque)
        assertEquals("card_uid:aa,card_uid:bb,batch=42,notes=foo", firstEncoded)

        // Second pass — fixed point.
        val secondPass = CardUidEncoding.decode(firstEncoded)
        assertEquals(firstPass.uids, secondPass.uids)
        assertEquals(firstPass.opaque, secondPass.opaque)
        assertEquals(firstEncoded, CardUidEncoding.encode(secondPass.uids, secondPass.opaque))
    }

    @Test
    fun `dedup is idempotent across multiple round-trips`() {
        val uids = listOf(CardUid("aa"), CardUid("aa"), CardUid("bb"))
        val encoded = CardUidEncoding.encode(uids)
        assertEquals("card_uid:aa,card_uid:bb", encoded)

        val decoded = CardUidEncoding.decode(encoded)
        assertEquals(listOf(CardUid("aa"), CardUid("bb")), decoded.uids)
        assertEquals(encoded, CardUidEncoding.encode(decoded.uids, decoded.opaque))
    }
}
