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

    /**
     * Type-to-search filter for the U21 picker search box (UI-48). Splits
     * [query] on whitespace and keeps the rows whose [textOf] contains **every**
     * token as a case-insensitive substring (AND), preserving input order (so
     * filtered results keep the picker's sort).
     *
     * Tokens are matched independently, which is the point (UI-60): a row's
     * searchable text is several fields joined with " · " separators — brand in
     * the primary line, material in the secondary — so a single contiguous
     * `contains` could never match "3dhojor petg" spanning both. It only ever
     * appeared to work when one field happened to repeat the other's words.
     * Matching per token makes brand + material queries work regardless of field
     * order or separators.
     *
     * A blank query is the identity — it returns [rows] unchanged, so the
     * no-query path is provably today's list and the U20 float can layer on top
     * of it. Query and row text are compared after trimming + lowercasing.
     */
    fun <T> filter(rows: List<T>, query: String, textOf: (T) -> String): List<T> {
        val needles = query.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
        if (needles.isEmpty()) return rows
        return rows.filter { row ->
            val haystack = textOf(row).lowercase()
            needles.all { haystack.contains(it) }
        }
    }

    private val WHITESPACE = Regex("\\s+")
}
