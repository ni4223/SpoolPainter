package com.spoolpainter.app.domain.primitives

/**
 * Pure list-reordering for the U20 picker surfacing features. Given an
 * already-sorted list of rows and a predicate that marks the "suggested" ones,
 * returns the suggested rows first (in their original relative order) followed
 * by the rest (also in original order).
 *
 * Kept free of Android / Compose types so it is unit-testable on the JVM. The
 * caller renders a thin divider between the two groups (no header label per
 * Q-U20-1). When nothing is suggested the output equals the input, so the
 * picker looks exactly as it does today.
 */
object PickerRanking {

    /**
     * A reordered list plus the count of leading suggested rows, so the caller
     * knows where to draw the divider. [suggestedCount] is the number of rows at
     * the front of [rows] that came from the suggested group; it is 0 when
     * nothing was suggested and equal to `rows.size` when everything was.
     */
    data class Result<T>(val rows: List<T>, val suggestedCount: Int)

    /**
     * Partition [rows] into (suggested, rest), preserving each group's existing
     * relative order, and concatenate suggested-first. Stable: two calls with
     * the same input produce the same output.
     */
    fun <T> partition(rows: List<T>, isSuggested: (T) -> Boolean): Result<T> {
        if (rows.isEmpty()) return Result(rows, 0)
        val suggested = ArrayList<T>()
        val rest = ArrayList<T>(rows.size)
        for (row in rows) {
            if (isSuggested(row)) suggested.add(row) else rest.add(row)
        }
        if (suggested.isEmpty()) return Result(rows, 0)
        return Result(suggested + rest, suggested.size)
    }

    /**
     * Like [partition] but the suggested group is ordered by [rankOf] (lower
     * rank first) instead of by the input order — so a match scorer's ranking
     * drives the float order, not the picker's default sort. [rankOf] returns
     * the rank for a suggested row, or null for a row that stays in "rest"
     * (kept in original order). Ties on rank preserve original order (stable).
     */
    fun <T> partitionRanked(rows: List<T>, rankOf: (T) -> Int?): Result<T> {
        if (rows.isEmpty()) return Result(rows, 0)
        val suggested = ArrayList<Pair<T, Int>>()
        val rest = ArrayList<T>(rows.size)
        for (row in rows) {
            val rank = rankOf(row)
            if (rank != null) suggested.add(row to rank) else rest.add(row)
        }
        if (suggested.isEmpty()) return Result(rows, 0)
        // sortedBy is stable, so equal ranks keep their original relative order.
        val ordered = suggested.sortedBy { it.second }.map { it.first }
        return Result(ordered + rest, ordered.size)
    }
}
