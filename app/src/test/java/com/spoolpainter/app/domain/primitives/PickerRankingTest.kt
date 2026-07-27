package com.spoolpainter.app.domain.primitives

import org.junit.Assert.assertEquals
import org.junit.Test

class PickerRankingTest {

    @Test
    fun `empty input returns empty with zero suggested`() {
        val result = PickerRanking.partition(emptyList<Int>()) { true }
        assertEquals(emptyList<Int>(), result.rows)
        assertEquals(0, result.suggestedCount)
    }

    @Test
    fun `nothing suggested returns input unchanged`() {
        val input = listOf(1, 2, 3, 4)
        val result = PickerRanking.partition(input) { false }
        assertEquals(input, result.rows)
        assertEquals(0, result.suggestedCount)
    }

    @Test
    fun `everything suggested returns input unchanged with full count`() {
        val input = listOf(1, 2, 3)
        val result = PickerRanking.partition(input) { true }
        assertEquals(input, result.rows)
        assertEquals(3, result.suggestedCount)
    }

    @Test
    fun `suggested rows float to front preserving relative order`() {
        // Suggest evens; sorted input is 1..6. Evens 2,4,6 keep their order,
        // then odds 1,3,5 keep theirs.
        val input = listOf(1, 2, 3, 4, 5, 6)
        val result = PickerRanking.partition(input) { it % 2 == 0 }
        assertEquals(listOf(2, 4, 6, 1, 3, 5), result.rows)
        assertEquals(3, result.suggestedCount)
    }

    @Test
    fun `single suggested row floats to front`() {
        val input = listOf("a", "b", "c")
        val result = PickerRanking.partition(input) { it == "c" }
        assertEquals(listOf("c", "a", "b"), result.rows)
        assertEquals(1, result.suggestedCount)
    }

    @Test
    fun `already-front suggested keeps order and preserves the rest`() {
        val input = listOf(10, 20, 30, 40)
        val result = PickerRanking.partition(input) { it < 25 }
        assertEquals(listOf(10, 20, 30, 40), result.rows)
        assertEquals(2, result.suggestedCount)
    }

    @Test
    fun `partition is stable across repeated calls`() {
        val input = listOf(5, 3, 8, 1, 9, 2)
        val a = PickerRanking.partition(input) { it > 4 }
        val b = PickerRanking.partition(input) { it > 4 }
        assertEquals(a.rows, b.rows)
        assertEquals(a.suggestedCount, b.suggestedCount)
    }

    @Test
    fun `partitionRanked orders suggested by rank not input order`() {
        // Input sorted 10,20,30,40. Suggest 40 (rank 0) and 20 (rank 1) — the
        // ranked group must come out 40,20 even though 20 precedes 40 in input.
        val input = listOf(10, 20, 30, 40)
        val rank = mapOf(40 to 0, 20 to 1)
        val result = PickerRanking.partitionRanked(input) { rank[it] }
        assertEquals(listOf(40, 20, 10, 30), result.rows)
        assertEquals(2, result.suggestedCount)
    }

    @Test
    fun `partitionRanked with nothing suggested returns input unchanged`() {
        val input = listOf(1, 2, 3)
        val result = PickerRanking.partitionRanked(input) { null }
        assertEquals(input, result.rows)
        assertEquals(0, result.suggestedCount)
    }

    @Test
    fun `partitionRanked ties keep original relative order (stable)`() {
        // Two rows share rank 0 — they keep their input order (b before c).
        val input = listOf("a", "b", "c", "d")
        val rank = mapOf("b" to 0, "c" to 0)
        val result = PickerRanking.partitionRanked(input) { rank[it] }
        assertEquals(listOf("b", "c", "a", "d"), result.rows)
        assertEquals(2, result.suggestedCount)
    }

    // --- filter (U21 / UI-48 type-to-search) ---

    @Test
    fun `filter blank query returns rows unchanged`() {
        val input = listOf("Elegoo PLA Red", "Bambu PLA White")
        assertEquals(input, PickerRanking.filter(input, "") { it })
        assertEquals(input, PickerRanking.filter(input, "   ") { it })
    }

    @Test
    fun `filter matches case-insensitive substring preserving order`() {
        val input = listOf("Elegoo PLA Red", "Bambu PLA White", "Hatchbox PLA Red")
        val result = PickerRanking.filter(input, "RED") { it }
        assertEquals(listOf("Elegoo PLA Red", "Hatchbox PLA Red"), result)
    }

    @Test
    fun `filter no match returns empty`() {
        val input = listOf("Elegoo PLA Red", "Bambu PLA White")
        assertEquals(emptyList<String>(), PickerRanking.filter(input, "PETG") { it })
    }

    @Test
    fun `filter trims surrounding whitespace on query`() {
        val input = listOf("Elegoo PLA Red", "Bambu PLA White")
        assertEquals(listOf("Bambu PLA White"), PickerRanking.filter(input, "  bambu  ") { it })
    }

    @Test
    fun `filter matches id via textOf projection`() {
        // Caller folds the numeric id into textOf; a bare number finds the row.
        data class Row(val name: String, val id: Int)
        val input = listOf(Row("Elegoo PLA Red", 12), Row("Bambu PLA White", 40))
        val result = PickerRanking.filter(input, "40") { "${it.name} #${it.id}" }
        assertEquals(listOf(Row("Bambu PLA White", 40)), result)
    }

    @Test
    fun `filter empty rows returns empty`() {
        assertEquals(emptyList<String>(), PickerRanking.filter(emptyList<String>(), "x") { it })
    }
}
