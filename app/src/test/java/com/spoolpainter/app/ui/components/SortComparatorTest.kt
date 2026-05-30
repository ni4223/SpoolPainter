package com.spoolpainter.app.ui.components

import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.data.local.SpoolSortKey
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import org.junit.Assert.assertEquals
import org.junit.Test

class SortComparatorTest {

    private fun spool(
        id: Int,
        vendorName: String? = null,
        filamentName: String? = null,
        material: String? = null,
        colorHex: String? = null,
        lastUsed: String? = null,
    ): SpoolmanSpool = SpoolmanSpool(
        id = id,
        filament = SpoolmanFilament(
            id = id,
            name = filamentName,
            material = material,
            vendor = vendorName?.let { SpoolmanVendor(name = it) },
            color_hex = colorHex,
        ),
        last_used = lastUsed,
    )

    private fun filament(
        id: Int,
        vendorName: String? = null,
        filamentName: String? = null,
        material: String? = null,
        colorHex: String? = null,
    ): SpoolmanFilament = SpoolmanFilament(
        id = id,
        name = filamentName,
        material = material,
        vendor = vendorName?.let { SpoolmanVendor(name = it) },
        color_hex = colorHex,
    )

    @Test
    fun `spoolComparator Id Desc sorts by id descending`() {
        val items = listOf(spool(id = 1), spool(id = 3), spool(id = 2))
        val sorted = items.sortedWith(spoolComparator(SpoolSortKey.Id, SortDirection.Desc))
        assertEquals(listOf(3, 2, 1), sorted.map { it.id })
    }

    @Test
    fun `spoolComparator Id Asc sorts by id ascending`() {
        val items = listOf(spool(id = 1), spool(id = 3), spool(id = 2))
        val sorted = items.sortedWith(spoolComparator(SpoolSortKey.Id, SortDirection.Asc))
        assertEquals(listOf(1, 2, 3), sorted.map { it.id })
    }

    @Test
    fun `spoolComparator Material Asc sorts case-insensitive by material with id-asc tiebreak`() {
        val items = listOf(
            spool(id = 1, material = "PLA"),
            spool(id = 2, material = "abs"),
            spool(id = 3, material = "PLA"),
        )
        val sorted = items.sortedWith(spoolComparator(SpoolSortKey.Material, SortDirection.Asc))
        assertEquals(listOf(2, 1, 3), sorted.map { it.id })
    }

    @Test
    fun `spoolComparator Brand Desc sorts by vendor name descending with id-desc tiebreak`() {
        val items = listOf(
            spool(id = 1, vendorName = "Bambu"),
            spool(id = 2, vendorName = "Anycubic"),
            spool(id = 3, vendorName = "Bambu"),
        )
        val sorted = items.sortedWith(spoolComparator(SpoolSortKey.Brand, SortDirection.Desc))
        assertEquals(listOf(3, 1, 2), sorted.map { it.id })
    }

    @Test
    fun `spoolComparator LastUsed Desc puts most recently used first nulls last`() {
        val items = listOf(
            spool(id = 1, lastUsed = "2026-05-20T10:00:00Z"),
            spool(id = 2, lastUsed = null),
            spool(id = 3, lastUsed = "2026-05-29T10:00:00Z"),
            spool(id = 4, lastUsed = "2026-05-25T10:00:00Z"),
        )
        val sorted = items.sortedWith(spoolComparator(SpoolSortKey.LastUsed, SortDirection.Desc))
        assertEquals(listOf(3, 4, 1, 2), sorted.map { it.id })
    }

    @Test
    fun `spoolComparator LastUsed Asc puts oldest first nulls still last`() {
        val items = listOf(
            spool(id = 1, lastUsed = "2026-05-20T10:00:00Z"),
            spool(id = 2, lastUsed = null),
            spool(id = 3, lastUsed = "2026-05-29T10:00:00Z"),
            spool(id = 4, lastUsed = "2026-05-25T10:00:00Z"),
        )
        val sorted = items.sortedWith(spoolComparator(SpoolSortKey.LastUsed, SortDirection.Asc))
        assertEquals(listOf(1, 4, 3, 2), sorted.map { it.id })
    }

    @Test
    fun `filamentComparator Id Desc sorts by id descending`() {
        val items = listOf(filament(id = 7), filament(id = 4), filament(id = 9))
        val sorted = items.sortedWith(filamentComparator(FilamentSortKey.Id, SortDirection.Desc))
        assertEquals(listOf(9, 7, 4), sorted.map { it.id })
    }

    @Test
    fun `filamentComparator Id Asc sorts by id ascending`() {
        val items = listOf(filament(id = 7), filament(id = 4), filament(id = 9))
        val sorted = items.sortedWith(filamentComparator(FilamentSortKey.Id, SortDirection.Asc))
        assertEquals(listOf(4, 7, 9), sorted.map { it.id })
    }

    @Test
    fun `filamentComparator Material Asc sorts case-insensitive with id tiebreak`() {
        val items = listOf(
            filament(id = 1, material = "PLA"),
            filament(id = 2, material = "abs"),
            filament(id = 3, material = "PLA"),
        )
        val sorted = items.sortedWith(filamentComparator(FilamentSortKey.Material, SortDirection.Asc))
        assertEquals(listOf(2, 1, 3), sorted.map { it.id })
    }

    @Test
    fun `filamentComparator Brand Asc sorts by vendor name ascending`() {
        val items = listOf(
            filament(id = 1, vendorName = "Polymaker"),
            filament(id = 2, vendorName = "ESun"),
            filament(id = 3, vendorName = "Anycubic"),
        )
        val sorted = items.sortedWith(filamentComparator(FilamentSortKey.Brand, SortDirection.Asc))
        assertEquals(listOf(3, 2, 1), sorted.map { it.id })
    }
}
