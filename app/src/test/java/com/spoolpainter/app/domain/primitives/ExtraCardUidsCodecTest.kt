package com.spoolpainter.app.domain.primitives

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtraCardUidsCodecTest {

    @Test
    fun `encode emptyList returns jsonEmptyString`() {
        assertEquals("\"\"", ExtraCardUidsCodec.encode(emptyList()))
    }

    @Test
    fun `encode singleUid returns jsonWrappedSingle`() {
        assertEquals("\"AABBCCDD\"", ExtraCardUidsCodec.encode(listOf(CardUid("AABBCCDD"))))
    }

    @Test
    fun `encode multipleUids returns jsonWrappedCommaJoined`() {
        assertEquals(
            "\"AABBCCDD,11223344\"",
            ExtraCardUidsCodec.encode(listOf(CardUid("AABBCCDD"), CardUid("11223344"))),
        )
    }

    @Test
    fun `decode emptyString returns emptyList`() {
        assertEquals(emptyList<CardUid>(), ExtraCardUidsCodec.decode(""))
    }

    @Test
    fun `decode jsonEmptyString returns emptyList`() {
        assertEquals(emptyList<CardUid>(), ExtraCardUidsCodec.decode("\"\""))
    }

    @Test
    fun `decode jsonSingleUid returns singleton`() {
        assertEquals(
            listOf(CardUid("AABBCCDD")),
            ExtraCardUidsCodec.decode("\"AABBCCDD\""),
        )
    }

    @Test
    fun `decode rawCommaJoined returns list`() {
        assertEquals(
            listOf(CardUid("AABBCCDD"), CardUid("11223344")),
            ExtraCardUidsCodec.decode("AABBCCDD,11223344"),
        )
    }

    @Test
    fun `decode jsonCommaJoined returns list`() {
        assertEquals(
            listOf(CardUid("AABBCCDD"), CardUid("11223344")),
            ExtraCardUidsCodec.decode("\"AABBCCDD,11223344\""),
        )
    }

    @Test
    fun `decode normalisesLowercaseToUppercase`() {
        assertEquals(
            listOf(CardUid("AABBCCDD")),
            ExtraCardUidsCodec.decode("\"aabbccdd\""),
        )
    }

    @Test
    fun `decode skipsInvalidHexEntry keepsValidNeighbours`() {
        assertEquals(
            listOf(CardUid("AABBCCDD"), CardUid("11223344")),
            ExtraCardUidsCodec.decode("\"AABBCCDD,??ZZ,11223344\""),
        )
    }

    @Test
    fun `decode handlesWhitespaceAndTrailingCommas`() {
        assertEquals(
            listOf(CardUid("AABBCCDD"), CardUid("11223344")),
            ExtraCardUidsCodec.decode("\" AABBCCDD , ,11223344,\""),
        )
    }

    @Test
    fun `decode roundTripsEncode`() {
        val original = listOf(CardUid("AABBCCDD"), CardUid("11223344"))
        assertEquals(original, ExtraCardUidsCodec.decode(ExtraCardUidsCodec.encode(original)))
    }
}
