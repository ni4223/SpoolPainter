package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.support.FakeMoveOnBindUseCase
import com.spoolpainter.app.support.FakeNfcRepository
import com.spoolpainter.app.support.FakeSpoolmanRepository
import com.spoolpainter.app.support.MoveOnBindNoOp
import com.spoolpainter.app.ui.screens.main.FormState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateAndPairUseCaseTest {

    private val nfc = FakeNfcRepository()
    private val spoolman = FakeSpoolmanRepository()
    private val moveOnBind = MoveOnBindNoOp
    private val useCase = CreateAndPairUseCase(nfc, spoolman, moveOnBind)

    private val sampleUid = CardUid("AABBCCDD")
    private val sampleSpool = SpoolmanSpool(
        id = 42,
        filament = SpoolmanFilament(id = 7, material = "PLA"),
    )

    private val baseForm = FormState(
        cardUid = sampleUid,
        material = Material("PLA", 190, 220, 55, 65),
        brand = Brand("Bambu"),
        colorHex = "FF0000",
        tempRanges = TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60),
    )

    private fun input(form: FormState = baseForm): CreateAndPairInput =
        CreateAndPairInput(form = form, newFilamentName = "Test", newFilamentVendor = "Test Vendor")

    private fun stageSingleTapSuccess() {
        // NfcRepository.runWriteThenVerify writes + verifies on the same tag
        // connection, so a single Success terminal state covers both phases.
        nfc.queueArmResults(NfcResult.Success(sampleUid, TagClassification.Blank))
    }

    @Test
    fun `tapFirst existingSpool writesAndVerifiesThenAppends`() = runTest {
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(sampleSpool)
        stageSingleTapSuccess()

        val result = useCase.invoke(input(baseForm.copy(selectedSpoolId = 42)))

        assertTrue("got $result", result is CreateAndPairResult.Success.WrittenAndPaired)
        val ok = result as CreateAndPairResult.Success.WrittenAndPaired
        assertEquals(42, ok.spoolId)
        assertEquals(sampleUid, ok.uid)
        assertEquals(false, ok.isNewSpool)
        assertEquals(1, spoolman.appendCalls)
        // Single arm for write+verify; no separate verify pass.
        assertEquals(1, nfc.armCalls)
    }

    @Test
    fun `existingSpool with variant typed - patches filament via applyVariantToFilamentOfSpool`() = runTest {
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(sampleSpool)
        stageSingleTapSuccess()

        // User picked spool #42, edited the Variant field to "Matte".
        val result = useCase.invoke(
            input(baseForm.copy(selectedSpoolId = 42, variant = "Matte")),
        )

        assertTrue("got $result", result is CreateAndPairResult.Success.WrittenAndPaired)
        // v2.0.2 (decision J): existing-spool path narrowed from
        // applyOverridesToFilamentOfSpool to applyVariantToFilamentOfSpool.
        assertEquals(1, spoolman.applyVariantToFilamentOfSpoolCalls)
        assertEquals(0, spoolman.applyOverridesToFilamentOfSpoolCalls)
        // Patch was scoped to the right spool with the typed variant.
        assertEquals(42, spoolman.lastApplyVariantToFilamentOfSpool?.first)
        assertEquals("Matte", spoolman.lastApplyVariantToFilamentOfSpool?.second)
        // UID still appended — pairing flow not aborted.
        assertEquals(1, spoolman.appendCalls)
    }

    @Test
    fun `existingSpool with no variant typed - skips variant patch entirely`() = runTest {
        // v2.0.2 (decision J): if the user hasn't typed a variant,
        // applyVariantToFilamentOfSpool is NOT called — there's nothing to
        // patch. The old behaviour fired the patch for every save and
        // relied on sparseDiff server-side; the new path doesn't bother.
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(sampleSpool)
        stageSingleTapSuccess()

        val result = useCase.invoke(
            input(baseForm.copy(selectedSpoolId = 42)), // variant defaults to null
        )

        assertTrue("got $result", result is CreateAndPairResult.Success.WrittenAndPaired)
        assertEquals(0, spoolman.applyVariantToFilamentOfSpoolCalls)
        assertEquals(0, spoolman.applyOverridesToFilamentOfSpoolCalls)
    }

    @Test
    fun `existingSpool with remaining edited - patchSpoolFields fires with remaining only`() = runTest {
        // v2.0.2 (decision F): stale-prefill guard. Remaining only PATCHes
        // when the form value differs from the prefill snapshot.
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(sampleSpool)
        stageSingleTapSuccess()

        useCase.invoke(
            input(
                baseForm.copy(
                    selectedSpoolId = 42,
                    remainingWeightG = 600f,
                    prefilledRemainingWeightG = 850f,
                ),
            ),
        )

        assertEquals(1, spoolman.patchSpoolFieldsCalls)
        assertEquals(42, spoolman.lastPatchSpoolFields?.first)
        assertEquals(600f, spoolman.lastPatchSpoolFields?.second?.remaining_weight)
        // No price change → null in body.
        assertEquals(null, spoolman.lastPatchSpoolFields?.second?.price)
    }

    @Test
    fun `existingSpool with remaining matching prefill - skips patchSpoolFields (stale guard)`() = runTest {
        // The CRITICAL stale-prefill case: form value equals prefill →
        // user didn't edit; do NOT overwrite Spoolman's possibly-fresher
        // value (printer firmware may have decremented it since prefill).
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(sampleSpool)
        stageSingleTapSuccess()

        useCase.invoke(
            input(
                baseForm.copy(
                    selectedSpoolId = 42,
                    remainingWeightG = 850f,
                    prefilledRemainingWeightG = 850f,
                ),
            ),
        )

        assertEquals(0, spoolman.patchSpoolFieldsCalls)
    }

    @Test
    fun `existingSpool price edits do NOT trigger patchSpoolFields (price locked)`() = runTest {
        // Decision M revised 2026-05-31: price is set at acquisition, not
        // a moving stock quote. Existing-spool path locks price at the UI
        // layer; even if a divergent priceMajor reaches the use case, no
        // PATCH should fire. Only remaining_weight is spool-scope dirty.
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(sampleSpool)
        stageSingleTapSuccess()

        useCase.invoke(
            input(
                baseForm.copy(
                    selectedSpoolId = 42,
                    priceMajor = 25.50f,
                    prefilledPriceMajor = 19.99f,
                    // Remaining matches prefill — nothing else dirty.
                    remainingWeightG = 850f,
                    prefilledRemainingWeightG = 850f,
                ),
            ),
        )

        assertEquals(0, spoolman.patchSpoolFieldsCalls)
    }

    @Test
    fun `newSpool path does NOT invoke applyOverridesToFilamentOfSpool`() = runTest {
        // The existing-spool variant edit shouldn't run for the new-spool
        // create path — that path handles variant via createSpoolForNewFilamentBundle
        // / createSpoolForExistingFilament directly, not through this seam.
        val newSpool = sampleSpool.copy(id = 99)
        spoolman.nextCreateSpoolResult = SpoolmanOutcome.Success(newSpool)
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(newSpool)
        stageSingleTapSuccess()

        val result = useCase.invoke(input(baseForm.copy(variant = "Matte")))  // selectedSpoolId = null → new-spool path

        assertTrue("got $result", result is CreateAndPairResult.Success.WrittenAndPaired)
        assertEquals(0, spoolman.applyOverridesToFilamentOfSpoolCalls)
    }

    @Test
    fun `tapFirst newSpool createsThenWritesAndVerifiesThenAppends`() = runTest {
        val newSpool = sampleSpool.copy(id = 99)
        spoolman.nextCreateSpoolResult = SpoolmanOutcome.Success(newSpool)
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(newSpool)
        stageSingleTapSuccess()

        val result = useCase.invoke(input())  // selectedSpoolId = null → new path

        assertTrue("got $result", result is CreateAndPairResult.Success.WrittenAndPaired)
        val ok = result as CreateAndPairResult.Success.WrittenAndPaired
        assertEquals(99, ok.spoolId)
        assertEquals(true, ok.isNewSpool)
        assertEquals(1, spoolman.createSpoolCalls)
        assertEquals(1, spoolman.appendCalls)
        assertEquals(sampleUid, spoolman.lastAppend?.second)
    }

    @Test
    fun `formFirst existingSpool capturesUidThenAppends`() = runTest {
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(sampleSpool)
        stageSingleTapSuccess()
        val result = useCase.invoke(
            input(baseForm.copy(cardUid = null, selectedSpoolId = 42)),
        )
        assertTrue("got $result", result is CreateAndPairResult.Success.WrittenAndPaired)
        assertEquals(sampleUid, spoolman.lastAppend?.second)
        assertEquals(1, nfc.armCalls)
    }

    @Test
    fun `formFirst newSpool createsSpoolThenCapturesUidThenAppends`() = runTest {
        val newSpool = sampleSpool.copy(id = 99)
        spoolman.nextCreateSpoolResult = SpoolmanOutcome.Success(newSpool)
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(newSpool)
        stageSingleTapSuccess()
        val result = useCase.invoke(input(baseForm.copy(cardUid = null)))
        assertTrue("got $result", result is CreateAndPairResult.Success.WrittenAndPaired)
        assertEquals(true, (result as CreateAndPairResult.Success.WrittenAndPaired).isNewSpool)
        assertEquals(1, spoolman.createSpoolCalls)
        assertEquals(0, spoolman.removeCalls)
        assertEquals(sampleUid, spoolman.lastAppend?.second)
    }

    @Test
    fun `verifyFailedDuringWrite returnsVerifyFailed`() = runTest {
        val newSpool = sampleSpool.copy(id = 99)
        spoolman.nextCreateSpoolResult = SpoolmanOutcome.Success(newSpool)
        // No append staged — the write surfaces verify mismatch before the
        // PATCH would run, so appendCalls stays at 0.
        nfc.queueArmResults(NfcResult.Error("verify mismatch: payload differs"))

        val result = useCase.invoke(input())

        assertTrue("got $result", result is CreateAndPairResult.VerifyFailed)
        val fail = result as CreateAndPairResult.VerifyFailed
        assertEquals(99, fail.spoolId)
        assertEquals(true, fail.isNewSpool)
        assertEquals(1, spoolman.createSpoolCalls)
        // Append did NOT run because the write itself failed verify; a retry
        // will create a fresh spool unless the user picks the orphaned one.
        assertEquals(0, spoolman.appendCalls)
    }

    @Test
    fun `idempotentAppend stillReturnsSuccess`() = runTest {
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(sampleSpool)
        stageSingleTapSuccess()

        val result = useCase.invoke(input(baseForm.copy(selectedSpoolId = 42)))

        assertTrue(result is CreateAndPairResult.Success.WrittenAndPaired)
    }

    @Test
    fun `moveOnBind NoOp proceedsWithoutBranch`() = runTest {
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(sampleSpool)
        stageSingleTapSuccess()

        val result = useCase.invoke(input(baseForm.copy(selectedSpoolId = 42)))
        assertTrue(result is CreateAndPairResult.Success.WrittenAndPaired)
    }

    @Test
    fun `appendError surfacesSpoolmanFailedAfterWrite`() = runTest {
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.HttpError(500, "boom")
        stageSingleTapSuccess()

        val result = useCase.invoke(input(baseForm.copy(selectedSpoolId = 42)))

        assertTrue("got $result", result is CreateAndPairResult.SpoolmanFailed)
        assertEquals(1, nfc.armCalls)
    }

    @Test
    fun `nfcWriteError returnsNfcFailedBeforeAppend`() = runTest {
        nfc.queueArmResults(NfcResult.Error("tag lost"))

        val result = useCase.invoke(input(baseForm.copy(selectedSpoolId = 42)))

        assertTrue("got $result", result is CreateAndPairResult.NfcFailed)
        assertEquals(1, nfc.armCalls)
        assertEquals(0, spoolman.appendCalls)
    }

    @Test
    fun `formFirst writeError surfacesNfcFailedWithoutUidRecorded`() = runTest {
        nfc.queueArmResults(NfcResult.Error("tag lost"))
        val result = useCase.invoke(
            input(baseForm.copy(cardUid = null, selectedSpoolId = 42)),
        )
        assertTrue(result is CreateAndPairResult.NfcFailed)
        assertEquals(0, spoolman.appendCalls)
    }

    @Test
    fun `createSpoolError surfacesSpoolmanFailedBeforeWrite`() = runTest {
        spoolman.nextCreateSpoolResult = SpoolmanOutcome.HttpError(503, "down")

        val result = useCase.invoke(input(baseForm.copy(cardUid = null)))

        assertTrue("got $result", result is CreateAndPairResult.SpoolmanFailed)
        assertEquals(0, nfc.armCalls)
    }

    @Test
    fun `move_on_bind declined returnsCancelledNoAppend`() = runTest {
        val moveOnBind = FakeMoveOnBindUseCase().apply {
            nextOutcome = MoveOnBindUseCase.Outcome.Declined
        }
        val useCase = CreateAndPairUseCase(nfc, spoolman, moveOnBind)
        stageSingleTapSuccess()

        val result = useCase.invoke(input(baseForm.copy(selectedSpoolId = 42)))

        assertTrue("got $result", result is CreateAndPairResult.Cancelled)
        assertEquals(0, spoolman.appendCalls)
        assertEquals(1, moveOnBind.invokeCalls)
    }

    @Test
    fun `move_on_bind failed returnsSpoolmanFailedNoAppend`() = runTest {
        val moveOnBind = FakeMoveOnBindUseCase().apply {
            nextOutcome = MoveOnBindUseCase.Outcome.Failed("simulated", partiallyModifiedSpoolIds = emptyList())
        }
        val useCase = CreateAndPairUseCase(nfc, spoolman, moveOnBind)
        stageSingleTapSuccess()

        val result = useCase.invoke(input(baseForm.copy(selectedSpoolId = 42)))

        assertTrue("got $result", result is CreateAndPairResult.SpoolmanFailed)
        assertEquals(0, spoolman.appendCalls)
    }

    @Test
    fun `selectedFilamentId path — happy invokes createSpoolForExistingFilament not resolveOrCreate`() = runTest {
        val newSpool = sampleSpool.copy(id = 77)
        spoolman.nextCreateSpoolForExistingFilamentResult = SpoolmanOutcome.Success(newSpool)
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(newSpool)
        stageSingleTapSuccess()

        val result = useCase.invoke(input(baseForm.copy(selectedFilamentId = 7)))

        assertTrue("got $result", result is CreateAndPairResult.Success.WrittenAndPaired)
        assertEquals(77, (result as CreateAndPairResult.Success.WrittenAndPaired).spoolId)
        assertEquals(true, result.isNewSpool)
        assertEquals(1, spoolman.createSpoolForExistingFilamentCalls)
        assertEquals(0, spoolman.createSpoolCalls) // resolveOrCreate path NOT taken
        assertEquals(7, spoolman.lastCreateForExisting?.first)
    }

    @Test
    fun `selectedFilamentId path — overrides locked, only variant flows (v2_0_2 decision I)`() = runTest {
        // v2.0.2 (decision I): when an existing filament is selected, the
        // identity + spec fields lock at the UI layer. toExpanderOverrides
        // drops density / weight / spool_weight / price defensively when
        // selectedFilamentId != null. The filament-create body uses stored
        // values; the new spool inherits filament-level price.
        val newSpool = sampleSpool.copy(id = 88)
        spoolman.nextCreateSpoolForExistingFilamentResult = SpoolmanOutcome.Success(newSpool)
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(newSpool)
        stageSingleTapSuccess()

        val formWithOverrides = baseForm.copy(
            selectedFilamentId = 7,
            fullSpoolWeightG = 750f,
            priceMajor = 19.99f,
            variant = "Matte",
        )
        useCase.invoke(input(formWithOverrides))

        // Variant still flows through (decision K — variant rides the tag write).
        assertEquals("Matte", spoolman.lastCreateForExisting?.second?.variant)
        // Spec fields locked: weight not propagated even if user typed it.
        assertEquals(null, spoolman.lastCreateForExisting?.second?.weight)
        // Price is filament-scope here (decision M new-spool path null when
        // selectedFilamentId != null because override bag drops price).
        assertEquals(null, spoolman.lastCreateForExisting?.second?.price)
    }
}
