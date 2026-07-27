package com.spoolpainter.app.domain.primitives

import com.spoolpainter.app.domain.primitives.SpoolMatchScorer.Candidate
import com.spoolpainter.app.domain.primitives.SpoolMatchScorer.Query
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoolMatchScorerTest {

    private fun cand(
        id: Int,
        material: String? = null,
        brand: String? = null,
        color: String? = null,
        variant: String? = null,
    ) = Candidate(filamentId = id, material = material, brand = brand, colorHex = color, variant = variant)

    @Test
    fun `empty inventory yields no suggestions`() {
        val q = Query(material = "PLA", brand = "Bambu", colorHex = "FF0000")
        assertEquals(emptyList<Int>(), SpoolMatchScorer.suggestedFilamentIds(q, emptyList()))
    }

    @Test
    fun `query with no usable signal yields nothing`() {
        val q = Query(material = null, brand = "", colorHex = "notahex")
        val candidates = listOf(cand(1, "PLA", "Bambu", "FF0000"))
        assertEquals(emptyList<Int>(), SpoolMatchScorer.suggestedFilamentIds(q, candidates))
    }

    @Test
    fun `no matching signal on any candidate yields nothing`() {
        val q = Query(material = "PLA", brand = "Bambu", colorHex = "FF0000")
        // Different material, different brand, far color (green).
        val candidates = listOf(cand(1, "PETG", "eSun", "00FF00"))
        assertEquals(emptyList<Int>(), SpoolMatchScorer.suggestedFilamentIds(q, candidates))
    }

    @Test
    fun `material match alone is enough to suggest`() {
        val q = Query(material = "PLA", brand = null, colorHex = null)
        val candidates = listOf(cand(1, "PLA", "eSun", "00FF00"), cand(2, "PETG", "eSun", "00FF00"))
        assertEquals(listOf(1), SpoolMatchScorer.suggestedFilamentIds(q, candidates))
    }

    @Test
    fun `material comparison is case-insensitive and trimmed`() {
        val q = Query(material = " pla ", brand = null, colorHex = null)
        val candidates = listOf(cand(1, "PLA"))
        assertEquals(listOf(1), SpoolMatchScorer.suggestedFilamentIds(q, candidates))
    }

    @Test
    fun `material plus brand outranks material alone`() {
        val q = Query(material = "PLA", brand = "Bambu", colorHex = null)
        val candidates = listOf(
            cand(1, "PLA", "eSun"),   // material only
            cand(2, "PLA", "Bambu"),  // material + brand
        )
        assertEquals(listOf(2, 1), SpoolMatchScorer.suggestedFilamentIds(q, candidates))
    }

    @Test
    fun `exact color match beats a far color when material ties`() {
        val q = Query(material = "PLA", brand = null, colorHex = "FF0000")
        val candidates = listOf(
            cand(1, "PLA", color = "0000FF"),  // far (blue)
            cand(2, "PLA", color = "FF0000"),  // exact (red)
        )
        assertEquals(listOf(2, 1), SpoolMatchScorer.suggestedFilamentIds(q, candidates))
    }

    @Test
    fun `far color adds nothing beyond the material match`() {
        // Two PLA candidates, one with a far color, one with no color. Both
        // score only the material weight, so the tie breaks on ascending id.
        val q = Query(material = "PLA", brand = null, colorHex = "FF0000")
        val candidates = listOf(
            cand(2, "PLA", color = "00FF00"),  // green — beyond closeness floor, no bonus
            cand(1, "PLA", color = null),
        )
        assertEquals(listOf(1, 2), SpoolMatchScorer.suggestedFilamentIds(q, candidates))
    }

    @Test
    fun `result is capped at three`() {
        val q = Query(material = "PLA", brand = null, colorHex = null)
        val candidates = (1..10).map { cand(it, "PLA") }
        val ids = SpoolMatchScorer.suggestedFilamentIds(q, candidates)
        assertEquals(3, ids.size)
        // All tie on material only; tie-break is ascending id.
        assertEquals(listOf(1, 2, 3), ids)
    }

    @Test
    fun `null candidate fields never crash and simply do not match`() {
        val q = Query(material = "PLA", brand = "Bambu", colorHex = "FF0000")
        val candidates = listOf(cand(1, null, null, null), cand(2, "PLA"))
        assertEquals(listOf(2), SpoolMatchScorer.suggestedFilamentIds(q, candidates))
    }

    @Test
    fun `scores are strictly positive for suggested candidates`() {
        val q = Query(material = "PLA", brand = "Bambu", colorHex = "FF0000")
        val candidates = listOf(cand(1, "PLA", "Bambu", "FF0000"))
        val ranked = SpoolMatchScorer.rank(q, candidates)
        assertEquals(1, ranked.size)
        assertTrue(ranked.first().score > 0.0)
    }

    // --- variant signal (U21 follow-up) ---

    @Test
    fun `variant breaks a tie between otherwise-identical filaments`() {
        // User scenario: scan red PLA Matte; inventory has red PLA in Basic,
        // Marble, Matte (same material + color, no brand). The Matte one must
        // float first; the other two follow in id order.
        val q = Query(material = "PLA", brand = null, colorHex = "FF0000", variant = "Matte")
        val candidates = listOf(
            cand(1, "PLA", color = "FF0000", variant = "Basic"),
            cand(2, "PLA", color = "FF0000", variant = "Marble"),
            cand(3, "PLA", color = "FF0000", variant = "Matte"),
        )
        val ids = SpoolMatchScorer.suggestedFilamentIds(q, candidates)
        assertEquals(listOf(3, 1, 2), ids)
    }

    @Test
    fun `variant never outranks a color match`() {
        // A right-variant / wrong-color spool must NOT beat a right-color /
        // wrong-variant spool: variant (1.0) is weighted below color (2.0).
        val q = Query(material = "PLA", brand = "Bambu", colorHex = "FF0000", variant = "Matte")
        val candidates = listOf(
            // exact red, no variant
            cand(1, "PLA", "Bambu", "FF0000"),
            // far blue, but Matte variant matches
            cand(2, "PLA", "Bambu", "0000FF", variant = "Matte"),
        )
        val ids = SpoolMatchScorer.suggestedFilamentIds(q, candidates)
        assertEquals(listOf(1, 2), ids)
    }

    @Test
    fun `variant matches leniently as a substring either direction`() {
        // Tag "Matte" against a hand-typed Spoolman "PLA Matte" (and reverse).
        val q = Query(material = "PLA", brand = null, colorHex = null, variant = "Matte")
        val candidates = listOf(
            cand(1, "PLA", variant = "Silk"),
            cand(2, "PLA", variant = "PLA Matte"),
        )
        val ranked = SpoolMatchScorer.rank(q, candidates)
        // Both share the material match; #2 also lands the lenient variant hit.
        assertEquals(2, ranked.first().filamentId)
    }

    @Test
    fun `blank or basic variant on either side does not match`() {
        val q = Query(material = "PLA", brand = null, colorHex = null, variant = null)
        val candidates = listOf(cand(1, "PLA", variant = "Matte"))
        val ranked = SpoolMatchScorer.rank(q, candidates)
        // material-only score, variant contributes nothing.
        assertEquals(3.0, ranked.first().score, 0.0001)
    }
}
