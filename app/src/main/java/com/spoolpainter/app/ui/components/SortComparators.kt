package com.spoolpainter.app.ui.components

import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.data.local.SpoolSortKey
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool

private val CI: Comparator<String> = String.CASE_INSENSITIVE_ORDER

fun spoolComparator(key: SpoolSortKey, direction: SortDirection): Comparator<SpoolmanSpool> {
    if (key == SpoolSortKey.LastUsed) {
        // Null last_used (never-consumed spools) always sort last regardless
        // of direction — "most recent first" on Desc and "oldest first" on
        // Asc both want unused spools at the bottom.
        val byTimestamp = Comparator<SpoolmanSpool> { a, b ->
            val aT = a.last_used
            val bT = b.last_used
            when {
                aT == null && bT == null -> 0
                aT == null -> 1
                bT == null -> -1
                else -> if (direction == SortDirection.Asc) aT.compareTo(bT) else bT.compareTo(aT)
            }
        }
        val tiebreak = if (direction == SortDirection.Asc) spoolIdAsc else spoolIdAsc.reversed()
        return byTimestamp.then(tiebreak)
    }
    val asc: Comparator<SpoolmanSpool> = when (key) {
        SpoolSortKey.Material ->
            Comparator<SpoolmanSpool> { a, b ->
                CI.compare(a.filament.material ?: "", b.filament.material ?: "")
            }.then(spoolIdAsc)
        SpoolSortKey.Brand ->
            Comparator<SpoolmanSpool> { a, b ->
                CI.compare(
                    a.filament.vendor?.name ?: "",
                    b.filament.vendor?.name ?: "",
                )
            }.then(spoolIdAsc)
        SpoolSortKey.Id -> spoolIdAsc
        SpoolSortKey.LastUsed -> error("handled above")
    }
    return if (direction == SortDirection.Asc) asc else asc.reversed()
}

fun filamentComparator(key: FilamentSortKey, direction: SortDirection): Comparator<SpoolmanFilament> {
    val asc: Comparator<SpoolmanFilament> = when (key) {
        FilamentSortKey.Material ->
            Comparator<SpoolmanFilament> { a, b ->
                CI.compare(a.material ?: "", b.material ?: "")
            }.then(filamentIdAsc)
        FilamentSortKey.Brand ->
            Comparator<SpoolmanFilament> { a, b ->
                CI.compare(a.vendor?.name ?: "", b.vendor?.name ?: "")
            }.then(filamentIdAsc)
        FilamentSortKey.Id -> filamentIdAsc
    }
    return if (direction == SortDirection.Asc) asc else asc.reversed()
}

private val spoolIdAsc: Comparator<SpoolmanSpool> =
    Comparator { a, b -> compareValues(a.id ?: Int.MIN_VALUE, b.id ?: Int.MIN_VALUE) }

private val filamentIdAsc: Comparator<SpoolmanFilament> =
    Comparator { a, b -> compareValues(a.id, b.id) }
