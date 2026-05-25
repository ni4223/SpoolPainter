package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.primitives.CardUid
import org.junit.Assert.assertEquals
import org.junit.Test

class CardUidEncodingDecodeTest {

    @Test
    fun `empty input yields empty Decoded`() {
        assertEquals(CardUidEncoding.Decoded(emptyList(), ""), CardUidEncoding.decode(""))
    }

    @Test
    fun `whitespace-only input yields empty Decoded`() {
        assertEquals(CardUidEncoding.Decoded(emptyList(), ""), CardUidEncoding.decode("   "))
    }

    @Test
    fun `single canonical UID`() {
        assertEquals(
            CardUidEncoding.Decoded(listOf(CardUid("aabb")), ""),
            CardUidEncoding.decode("card_uid:aabb"),
        )
    }

    @Test
    fun `multi UID`() {
        assertEquals(
            CardUidEncoding.Decoded(listOf(CardUid("aabb"), CardUid("ccdd")), ""),
            CardUidEncoding.decode("card_uid:aabb,card_uid:ccdd"),
        )
    }

    @Test
    fun `uppercase prefix tolerated and hex normalised to lowercase`() {
        assertEquals(
            CardUidEncoding.Decoded(listOf(CardUid("aabb")), ""),
            CardUidEncoding.decode("CARD_UID:AABB"),
        )
    }

    @Test
    fun `mixed-case prefix tolerated`() {
        assertEquals(
            CardUidEncoding.Decoded(listOf(CardUid("ccdd")), ""),
            CardUidEncoding.decode("Card_Uid:ccdd"),
        )
    }

    @Test
    fun `surrounding whitespace around entry tolerated`() {
        assertEquals(
            CardUidEncoding.Decoded(listOf(CardUid("aabb")), ""),
            CardUidEncoding.decode(" card_uid:aabb "),
        )
    }

    @Test
    fun `non-hex value falls through to opaque verbatim`() {
        assertEquals(
            CardUidEncoding.Decoded(emptyList(), "card_uid:zz"),
            CardUidEncoding.decode("card_uid:zz"),
        )
    }

    @Test
    fun `odd-length value falls through to opaque verbatim`() {
        assertEquals(
            CardUidEncoding.Decoded(emptyList(), "card_uid:abc"),
            CardUidEncoding.decode("card_uid:abc"),
        )
    }

    @Test
    fun `empty value after prefix falls through to opaque`() {
        assertEquals(
            CardUidEncoding.Decoded(emptyList(), "card_uid:"),
            CardUidEncoding.decode("card_uid:"),
        )
    }

    @Test
    fun `opaque-only input preserved`() {
        assertEquals(
            CardUidEncoding.Decoded(emptyList(), "batch=42"),
            CardUidEncoding.decode("batch=42"),
        )
    }

    @Test
    fun `interleaved UIDs and opaque entries preserved separately`() {
        assertEquals(
            CardUidEncoding.Decoded(
                uids = listOf(CardUid("aa"), CardUid("bb")),
                opaque = "batch=42,notes=foo",
            ),
            CardUidEncoding.decode("card_uid:aa,batch=42,card_uid:bb,notes=foo"),
        )
    }

    @Test
    fun `double-comma fragments are skipped`() {
        assertEquals(
            CardUidEncoding.Decoded(listOf(CardUid("aa"), CardUid("bb")), ""),
            CardUidEncoding.decode("card_uid:aa,,card_uid:bb"),
        )
    }

    @Test
    fun `leading and trailing comma handled`() {
        assertEquals(
            CardUidEncoding.Decoded(listOf(CardUid("aa")), ""),
            CardUidEncoding.decode(",card_uid:aa,"),
        )
    }

    @Test
    fun `opaque content not downcased`() {
        assertEquals(
            CardUidEncoding.Decoded(emptyList(), "Notes:Backup"),
            CardUidEncoding.decode("Notes:Backup"),
        )
    }

    @Test
    fun `malformed card_uid entry preserves original surrounding whitespace`() {
        // " card_uid:zz " — original whitespace MUST be preserved per BR-U2-DEC-7.
        assertEquals(
            CardUidEncoding.Decoded(emptyList(), " card_uid:zz "),
            CardUidEncoding.decode(" card_uid:zz "),
        )
    }
}
