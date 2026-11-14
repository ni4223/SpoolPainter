package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.primitives.CardUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** FormState.articleNumber: where it is prefilled from and how it reaches Save. */
class FormArticleNumberTest {

    private val uid = CardUid("0a1b")
    private fun filament(articleNumber: String?) = SpoolmanFilament(
        id = 10, material = "PLA", vendor = SpoolmanVendor(name = "Anycubic"), article_number = articleNumber,
    )

    @Test
    fun `spool and filament prefill show Spoolman's article number`() {
        val f = filament("AHPLRR-107")
        assertEquals("AHPLRR-107", FormMapping.fromSpoolman(SpoolmanSpool(id = 1, filament = f), uid, false).articleNumber)
        assertEquals("AHPLRR-107", FormMapping.fromFilament(f, rawWriteMode = false).articleNumber)
    }

    @Test
    fun `blank Spoolman article number reads as none`() {
        assertNull(FormMapping.fromFilament(filament("  "), rawWriteMode = false).articleNumber)
    }

    private val tagUid = CardUid("04AABB")
    private val tag = ObservedTagKind.Vendor(sku = "HPL18-106")

    private fun state(form: FormState, kind: ObservedTagKind = tag) =
        MainUiState(form = form, observedTagKind = kind, observedTagUid = tagUid)

    @Test
    fun `tag's code shows for a new filament built from the tag`() {
        val st = state(FormState(cardUid = tagUid))
        assertEquals("HPL18-106", st.effectiveForm().articleNumber)
        assertEquals("HPL18-106", st.pendingTagSku())
    }

    @Test
    fun `a picked spool with no article number shows the tag's code`() {
        val picked = state(FormState(selectedSpoolId = 3, selectedFilamentId = 10, cardUid = tagUid))
        assertEquals("HPL18-106", picked.effectiveForm().articleNumber)
        assertEquals("HPL18-106", picked.pendingTagSku())
    }

    @Test
    fun `Spoolman's article number wins over the tag's`() {
        val form = FormState(selectedSpoolId = 3, selectedFilamentId = 10, cardUid = tagUid, articleNumber = "AHPLRR-107")
        assertEquals("AHPLRR-107", state(form).effectiveForm().articleNumber)
        assertNull(state(form).pendingTagSku())
    }

    @Test
    fun `no code on the tag, or no vendor tag - nothing`() {
        assertNull(state(FormState(), ObservedTagKind.Vendor()).effectiveForm().articleNumber)
        assertNull(state(FormState(), ObservedTagKind.None).effectiveForm().articleNumber)
        assertNull(state(FormState(), ObservedTagKind.None).pendingTagSku())
    }

    @Test
    fun `forgetTagSku keeps the Vendor kind but drops its code`() {
        val forgotten = state(FormState()).forgetTagSku()
        assertEquals(ObservedTagKind.Vendor(sku = null), forgotten.observedTagKind)
        assertNull(forgotten.effectiveForm().articleNumber)
    }

    @Test
    fun `only the new-filament save carries the article number`() {
        val form = FormState(articleNumber = "HPL18-106")
        assertNull(form.copy(selectedSpoolId = 1).toExpanderOverrides().articleNumber)
        assertNull(form.copy(selectedFilamentId = 2).toExpanderOverrides().articleNumber)
        assertEquals("HPL18-106", form.toExpanderOverrides().articleNumber)
    }
}
