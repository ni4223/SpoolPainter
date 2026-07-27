package com.spoolpainter.app.domain.primitives

import kotlin.math.sqrt

/**
 * Pure heuristic scorer for the U20 scan-time surfacing feature (UI-49).
 *
 * When a tag is read that is NOT already paired to a spool (vendor tag, or an
 * OpenSpool tag with no matching `card_uids`), we score the Spoolman filament
 * inventory against whatever the tag decoded to and float the best matches to
 * the top of the pickers. Per the locked Q&A (2026-07-27):
 *  - Signals are **material, brand, color, variant** — temperatures are NOT used.
 *  - Missing/unknown signals simply don't contribute (graceful degradation, so
 *    a material+color-only tag still ranks sensibly).
 *  - A candidate is "suggested" only when its score is > 0 (at least one real
 *    signal matched); the result is capped at [SUGGESTED_CAP] (top 3).
 *
 * Variant (U21 follow-up 2026-07-27) is the weakest signal on purpose: on the
 * tag it comes from the vendor payload's `subtype` (e.g. Bambu "Matte"), but on
 * the Spoolman side it is **free text the user typed by hand**, so it rarely
 * lines up exactly. It is matched **leniently** (case-insensitive substring,
 * either direction) so realistic variations ("Matte" vs "PLA Matte") still hit,
 * and weighted **below color** — color is a hex we can grade numerically and
 * trust, variant is a flaky field that should only break ties.
 *
 * Free of Android / domain-model types so it is unit-testable on the JVM; the
 * caller maps `SpoolmanFilament` into [Candidate].
 */
object SpoolMatchScorer {

    /** Maximum number of filaments floated to the top (Q-U20-3 = 3). */
    const val SUGGESTED_CAP: Int = 3

    // Additive signal weights (starting point; tuned on-device at the install
    // gate). Material and brand are strong exact-match signals; color is a
    // graded closeness signal so a near-miss shade still helps rank.
    private const val MATERIAL_WEIGHT = 3.0
    private const val BRAND_WEIGHT = 2.0
    private const val COLOR_WEIGHT = 2.0

    // Variant is intentionally the weakest signal (below color): it is
    // hand-entered free text in Spoolman, so it should only break ties between
    // otherwise-equal matches, never override material / brand / color.
    private const val VARIANT_WEIGHT = 1.0

    // Color only contributes when the two colors are at least this close
    // (1.0 = identical, 0.0 = maximally far), so an unrelated color adds no
    // noise to the score.
    private const val COLOR_MIN_CLOSENESS = 0.5

    // sqrt(255^2 * 3) — the largest possible RGB Euclidean distance.
    private val MAX_RGB_DISTANCE = sqrt(3.0 * 255.0 * 255.0)

    /** A filament reduced to the fields we rank on. */
    data class Candidate(
        val filamentId: Int,
        val material: String?,
        val brand: String?,
        val colorHex: String?,
        val variant: String? = null,
    )

    /** What the tag decoded to. Any field may be null/blank. */
    data class Query(
        val material: String?,
        val brand: String?,
        val colorHex: String?,
        val variant: String? = null,
    )

    data class Ranked(val filamentId: Int, val score: Double)

    /**
     * Score every candidate, keep those with score > 0, sort by score
     * descending (ties broken by ascending filamentId for stability), and cap
     * at [SUGGESTED_CAP]. Returns empty when nothing scores — the pickers then
     * render exactly as today.
     */
    fun rank(query: Query, candidates: List<Candidate>): List<Ranked> {
        if (candidates.isEmpty()) return emptyList()
        // A query with no usable signal can't suggest anything.
        val hasSignal = !query.material.isNullOrBlank() ||
            !query.brand.isNullOrBlank() ||
            !query.variant.isNullOrBlank() ||
            ColorHexCodec.toRgb(query.colorHex) != null
        if (!hasSignal) return emptyList()

        val queryRgb = ColorHexCodec.toRgb(query.colorHex)
        return candidates
            .map { Ranked(it.filamentId, score(query, queryRgb, it)) }
            .filter { it.score > 0.0 }
            .sortedWith(compareByDescending<Ranked> { it.score }.thenBy { it.filamentId })
            .take(SUGGESTED_CAP)
    }

    /** Convenience: just the suggested filament ids, best-first. */
    fun suggestedFilamentIds(query: Query, candidates: List<Candidate>): List<Int> =
        rank(query, candidates).map { it.filamentId }

    private fun score(
        query: Query,
        queryRgb: Triple<Int, Int, Int>?,
        candidate: Candidate,
    ): Double {
        var total = 0.0
        if (matches(query.material, candidate.material)) total += MATERIAL_WEIGHT
        if (matches(query.brand, candidate.brand)) total += BRAND_WEIGHT
        if (queryRgb != null) {
            val candRgb = ColorHexCodec.toRgb(candidate.colorHex)
            if (candRgb != null) {
                val closeness = 1.0 - distance(queryRgb, candRgb) / MAX_RGB_DISTANCE
                if (closeness >= COLOR_MIN_CLOSENESS) total += closeness * COLOR_WEIGHT
            }
        }
        if (matchesLenient(query.variant, candidate.variant)) total += VARIANT_WEIGHT
        return total
    }

    private fun matches(a: String?, b: String?): Boolean =
        !a.isNullOrBlank() && !b.isNullOrBlank() && a.trim().equals(b.trim(), ignoreCase = true)

    /**
     * Lenient match for the variant signal: case-insensitive substring in
     * either direction, so a tag's "Matte" matches a hand-typed Spoolman
     * "PLA Matte" and vice versa. Blank on either side is not a match.
     */
    private fun matchesLenient(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val x = a.trim().lowercase()
        val y = b.trim().lowercase()
        return x.contains(y) || y.contains(x)
    }

    private fun distance(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Double {
        val dr = (a.first - b.first).toDouble()
        val dg = (a.second - b.second).toDouble()
        val db = (a.third - b.third).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }
}
