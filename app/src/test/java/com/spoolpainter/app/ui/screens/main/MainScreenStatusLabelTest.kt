package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards `computeStatusLabel`, which decides whether the centered NFC overlay
 * appears at all (`NfcStatusOverlay` is gated on `label != null`).
 *
 * Written because `ActiveFlow.WritingRaw` — the no-Spoolman write — was never
 * added here, so that flow showed a Cancel button with no caption and no
 * animation while the otherwise-identical Spoolman write showed both.
 * `isWriteCancellable` in the ViewModel *did* know about the flow. Two surfaces
 * read the same state and only one was updated when the flow was introduced,
 * which is the bug class [everyActiveFlowIsClassified] exists to stop.
 */
class MainScreenStatusLabelTest {

    /**
     * Flows that block on a physical tag tap. Each MUST produce a caption: the
     * user is being asked to do something and has no other cue.
     */
    private val tagWaitingFlows: Map<ActiveFlow, String> = mapOf(
        ActiveFlow.ReadingForPair to "Tap a tag to read",
        ActiveFlow.WritingForPair to "Tap a tag to write",
        ActiveFlow.WritingRaw to "Tap a tag to write",
        ActiveFlow.WritingSecondTag(spoolId = 1) to "Tap second tag to pair",
        ActiveFlow.PairingVendorUidOnly to "Linking tag to spool",
    )

    /**
     * Flows that deliberately have no caption. Each already owns the screen with
     * its own surface (a sheet) or is not a flow at all, so an overlay on top
     * would either cover it or contradict it.
     */
    private val deliberatelySilentFlows: List<ActiveFlow> = listOf(
        ActiveFlow.Idle,
        ActiveFlow.PromptingPairAnother(spoolId = 1),
        ActiveFlow.AwaitingRepairConfirmation(
            uid = CardUid("AABBCCDD"),
            currentOwners = emptyList(),
            targetSpoolId = 1,
        ),
    )

    @Test
    fun `every tag-waiting flow produces its caption`() {
        tagWaitingFlows.forEach { (flow, expected) ->
            // Idle is the state between arming and the first tag event, which is
            // when the user is actually staring at the screen waiting.
            assertEquals("$flow with NfcResult.Idle", expected, computeStatusLabel(flow, NfcResult.Idle))
        }
    }

    @Test
    fun `the no-Spoolman write shows the same caption as the Spoolman write`() {
        // The regression. Before the fix this returned null and the overlay never
        // appeared, so pressing Write with no Spoolman URL looked like nothing
        // had happened.
        assertEquals(
            computeStatusLabel(ActiveFlow.WritingForPair, NfcResult.Idle),
            computeStatusLabel(ActiveFlow.WritingRaw, NfcResult.Idle),
        )
        assertNotNull(computeStatusLabel(ActiveFlow.WritingRaw, NfcResult.Idle))
    }

    @Test
    fun `a write in progress keeps its caption`() {
        // Once the tag is on the coil the repository moves to Writing. Dropping
        // the caption there would blink the overlay out mid-tap.
        assertEquals("Tap a tag to write", computeStatusLabel(ActiveFlow.WritingRaw, NfcResult.Writing))
        assertEquals("Tap a tag to write", computeStatusLabel(ActiveFlow.WritingForPair, NfcResult.Writing))
    }

    @Test
    fun `verifying is captioned for every write flow`() {
        listOf(
            ActiveFlow.WritingForPair,
            ActiveFlow.WritingRaw,
            ActiveFlow.WritingSecondTag(spoolId = 1),
        ).forEach { flow ->
            assertEquals("$flow", "Verifying tag", computeStatusLabel(flow, NfcResult.Verifying))
        }
    }

    @Test
    fun `flows that own the screen stay silent`() {
        deliberatelySilentFlows.forEach { flow ->
            assertNull("$flow should not raise an overlay", computeStatusLabel(flow, NfcResult.Idle))
        }
    }

    @Test
    fun `a terminal NFC result clears the caption`() {
        // Success/Error mean the tap is done; the overlay must come down even if
        // activeFlow has not been reset yet, or it would sit over the snackbar.
        assertNull(computeStatusLabel(ActiveFlow.WritingRaw, NfcResult.Success(CardUid("AABBCCDD"), com.spoolpainter.app.domain.primitives.TagClassification.Blank)))
        assertNull(computeStatusLabel(ActiveFlow.WritingRaw, NfcResult.Error("boom")))
    }

    /**
     * The exhaustiveness guard, and the real point of this file. Enumerates
     * `ActiveFlow`'s sealed subclasses via reflection and requires every one to
     * be classified above as either tag-waiting or deliberately silent.
     *
     * Adding a new `ActiveFlow` therefore fails this test until someone decides
     * whether it needs a caption — which is exactly the decision that was
     * skipped when `WritingRaw` was added.
     */
    @Test
    fun everyActiveFlowIsClassified() {
        val classified = (tagWaitingFlows.keys.map { it::class } + deliberatelySilentFlows.map { it::class })
            .toSet()
        val declared = ActiveFlow::class.sealedSubclasses.toSet()

        val unclassified = declared - classified
        assertTrue(
            "New ActiveFlow(s) $unclassified are not classified in MainScreenStatusLabelTest. " +
                "Decide whether each needs an NFC status caption, then add it to " +
                "tagWaitingFlows or deliberatelySilentFlows.",
            unclassified.isEmpty(),
        )
        // Cheap symmetry check: nothing classified that no longer exists.
        assertEquals(declared, classified)
    }
}
