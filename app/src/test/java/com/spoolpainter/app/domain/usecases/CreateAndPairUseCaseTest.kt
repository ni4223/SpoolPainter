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

/**
 * U13 — write-only orchestration. Spool resolution + variant + spool-scope
 * patches now live in [SaveToSpoolmanUseCase] (covered by
 * [SaveToSpoolmanUseCaseTest]); this test covers the Write half: arm NFC →
 * write → PATCH UID into `extra.card_uids` → translate write outcome.
 */
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

    private fun input(
        spoolId: Int = 42,
        isNewSpool: Boolean = false,
        form: FormState = baseForm,
    ): CreateAndPairInput = CreateAndPairInput(
        spoolId = spoolId,
        isNewSpool = isNewSpool,
        form = form,
        newFilamentName = "Test",
        newFilamentVendor = "Test Vendor",
    )

    private fun stageSingleTapSuccess() {
        // NfcRepository.runWriteThenVerify writes + verifies on the same tag
        // connection, so a single Success terminal state covers both phases.
        nfc.queueArmResults(NfcResult.Success(sampleUid, TagClassification.Blank))
    }

    @Test
    fun `existingSpool writesAndVerifiesThenAppends`() = runTest {
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(sampleSpool)
        stageSingleTapSuccess()

        val result = useCase.invoke(input(spoolId = 42, isNewSpool = false))

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
    fun `newSpool surfaces isNewSpool=true on success`() = runTest {
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(sampleSpool.copy(id = 99))
        stageSingleTapSuccess()

        val result = useCase.invoke(input(spoolId = 99, isNewSpool = true))

        assertTrue("got $result", result is CreateAndPairResult.Success.WrittenAndPaired)
        assertEquals(true, (result as CreateAndPairResult.Success.WrittenAndPaired).isNewSpool)
    }

    @Test
    fun `formFirst capturesUidThenAppends`() = runTest {
        // Tap-first/form-first distinction is now a UI concern: spool already
        // exists, just verify the write captures the UID end-to-end.
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(sampleSpool)
        stageSingleTapSuccess()

        val result = useCase.invoke(input(form = baseForm.copy(cardUid = null)))

        assertTrue("got $result", result is CreateAndPairResult.Success.WrittenAndPaired)
        assertEquals(sampleUid, spoolman.lastAppend?.second)
        assertEquals(1, nfc.armCalls)
    }

    @Test
    fun `verifyFailedDuringWrite returnsVerifyFailed`() = runTest {
        nfc.queueArmResults(NfcResult.Error("verify mismatch: payload differs"))

        val result = useCase.invoke(input(spoolId = 99, isNewSpool = true))

        assertTrue("got $result", result is CreateAndPairResult.VerifyFailed)
        val fail = result as CreateAndPairResult.VerifyFailed
        assertEquals(99, fail.spoolId)
        assertEquals(true, fail.isNewSpool)
        // Append did NOT run because the write itself failed verify.
        assertEquals(0, spoolman.appendCalls)
    }

    @Test
    fun `idempotentAppend stillReturnsSuccess`() = runTest {
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(sampleSpool)
        stageSingleTapSuccess()

        val result = useCase.invoke(input())

        assertTrue(result is CreateAndPairResult.Success.WrittenAndPaired)
    }

    @Test
    fun `appendError surfacesSpoolmanFailedAfterWrite`() = runTest {
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.HttpError(500, "boom")
        stageSingleTapSuccess()

        val result = useCase.invoke(input())

        assertTrue("got $result", result is CreateAndPairResult.SpoolmanFailed)
        assertEquals(1, nfc.armCalls)
    }

    @Test
    fun `nfcWriteError returnsNfcFailedBeforeAppend`() = runTest {
        nfc.queueArmResults(NfcResult.Error("tag lost"))

        val result = useCase.invoke(input())

        assertTrue("got $result", result is CreateAndPairResult.NfcFailed)
        assertEquals(1, nfc.armCalls)
        assertEquals(0, spoolman.appendCalls)
    }

    @Test
    fun `formFirst writeError surfacesNfcFailedWithoutUidRecorded`() = runTest {
        nfc.queueArmResults(NfcResult.Error("tag lost"))

        val result = useCase.invoke(input(form = baseForm.copy(cardUid = null)))

        assertTrue(result is CreateAndPairResult.NfcFailed)
        assertEquals(0, spoolman.appendCalls)
    }

    @Test
    fun `move_on_bind declined returnsCancelledNoAppend`() = runTest {
        val mob = FakeMoveOnBindUseCase().apply {
            nextOutcome = MoveOnBindUseCase.Outcome.Declined
        }
        val useCase = CreateAndPairUseCase(nfc, spoolman, mob)
        stageSingleTapSuccess()

        val result = useCase.invoke(input())

        assertTrue("got $result", result is CreateAndPairResult.Cancelled)
        assertEquals(0, spoolman.appendCalls)
        assertEquals(1, mob.invokeCalls)
    }

    @Test
    fun `move_on_bind failed returnsSpoolmanFailedNoAppend`() = runTest {
        val mob = FakeMoveOnBindUseCase().apply {
            nextOutcome = MoveOnBindUseCase.Outcome.Failed("simulated", partiallyModifiedSpoolIds = emptyList())
        }
        val useCase = CreateAndPairUseCase(nfc, spoolman, mob)
        stageSingleTapSuccess()

        val result = useCase.invoke(input())

        assertTrue("got $result", result is CreateAndPairResult.SpoolmanFailed)
        assertEquals(0, spoolman.appendCalls)
    }
}
