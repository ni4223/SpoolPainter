package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.primitives.CardUid
import org.junit.Assert.assertEquals
import org.junit.Test

class CardUidEncodingEncodeTest {

    @Test
    fun `empty list and empty opaque yields empty string`() {
        assertEquals("", CardUidEncoding.encode(emptyList(), ""))
    }

    @Test
    fun `single UID empty opaque`() {
        assertEquals(
            "card_uid:aabb",
            CardUidEncoding.encode(listOf(CardUid("aabb"))),
        )
    }

    @Test
    fun `multi UID empty opaque`() {
        assertEquals(
            "card_uid:aabb,card_uid:ccdd",
            CardUidEncoding.encode(listOf(CardUid("aabb"), CardUid("ccdd"))),
        )
    }

    @Test
    fun `duplicate UIDs are deduped to a single entry`() {
        assertEquals(
            "card_uid:aabb",
            CardUidEncoding.encode(listOf(CardUid("aabb"), CardUid("aabb"))),
        )
    }

    @Test
    fun `dedup preserves first-seen order`() {
        assertEquals(
            "card_uid:bb,card_uid:aa",
            CardUidEncoding.encode(listOf(CardUid("bb"), CardUid("aa"), CardUid("bb"))),
        )
    }

    @Test
    fun `empty UIDs with non-empty opaque emits opaque verbatim with no leading comma`() {
        assertEquals("batch=42", CardUidEncoding.encode(emptyList(), "batch=42"))
    }

    @Test
    fun `UIDs and opaque combined`() {
        assertEquals(
            "card_uid:aabb,batch=42",
            CardUidEncoding.encode(listOf(CardUid("aabb")), "batch=42"),
        )
    }

    @Test
    fun `multi UIDs and opaque combined`() {
        assertEquals(
            "card_uid:aabb,card_uid:ccdd,batch=42,notes=foo",
            CardUidEncoding.encode(
                listOf(CardUid("aabb"), CardUid("ccdd")),
                "batch=42,notes=foo",
            ),
        )
    }
}
