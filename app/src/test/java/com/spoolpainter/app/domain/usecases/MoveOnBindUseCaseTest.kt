package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.support.FakeMoveOnBindConfirmer
import com.spoolpainter.app.support.FakeSpoolmanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoveOnBindUseCaseTest {

    private val spoolman = FakeSpoolmanRepository()
    private val confirmer = FakeMoveOnBindConfirmer()
    private val useCase = MoveOnBindUseCaseImpl(spoolman, confirmer)

    private val uid = CardUid("AABBCCDD")

    private fun spool(id: Int): SpoolmanSpool = SpoolmanSpool(
        id = id,
        filament = SpoolmanFilament(id = id, material = "PLA"),
    )

    @Test
    fun `proceed when no owners`() = runTest {
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())
        val outcome = useCase.invoke(uid, targetSpoolId = 42)
        assertEquals(MoveOnBindUseCase.Outcome.Proceed, outcome)
        assertEquals(0, confirmer.confirmCalls)
    }

    @Test
    fun `proceed when uid already on target`() = runTest {
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(listOf(spool(42)))
        val outcome = useCase.invoke(uid, targetSpoolId = 42)
        assertEquals(MoveOnBindUseCase.Outcome.Proceed, outcome)
        assertEquals(0, confirmer.confirmCalls)
    }

    @Test
    fun `moved when owner is different and user confirms`() = runTest {
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(listOf(spool(7)))
        spoolman.nextRemoveCardUidResult = SpoolmanOutcome.Success(spool(7))
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(spool(42))
        confirmer.nextResult = true

        val outcome = useCase.invoke(uid, targetSpoolId = 42)

        assertEquals(MoveOnBindUseCase.Outcome.Moved(fromSpoolIds = listOf(7)), outcome)
        assertEquals(1, confirmer.confirmCalls)
        assertEquals(1, spoolman.removeCalls)
        assertEquals(1, spoolman.appendCalls)
    }

    @Test
    fun `declined when user cancels`() = runTest {
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(listOf(spool(7)))
        confirmer.nextResult = false

        val outcome = useCase.invoke(uid, targetSpoolId = 42)

        assertEquals(MoveOnBindUseCase.Outcome.Declined, outcome)
        assertEquals(1, confirmer.confirmCalls)
        assertEquals(0, spoolman.removeCalls)
        assertEquals(0, spoolman.appendCalls)
    }

    @Test
    fun `failed when remove fails - no partial`() = runTest {
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(listOf(spool(7)))
        spoolman.nextRemoveCardUidResult = SpoolmanOutcome.HttpError(500, "boom")
        confirmer.nextResult = true

        val outcome = useCase.invoke(uid, targetSpoolId = 42)

        assertTrue("got $outcome", outcome is MoveOnBindUseCase.Outcome.Failed)
        val failed = outcome as MoveOnBindUseCase.Outcome.Failed
        assertEquals(emptyList<Int>(), failed.partiallyModifiedSpoolIds)
        assertEquals(0, spoolman.appendCalls)
    }

    @Test
    fun `failed with partial when append fails after remove succeeds`() = runTest {
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(listOf(spool(7)))
        spoolman.nextRemoveCardUidResult = SpoolmanOutcome.Success(spool(7))
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.HttpError(500, "boom")
        confirmer.nextResult = true

        val outcome = useCase.invoke(uid, targetSpoolId = 42)

        assertTrue("got $outcome", outcome is MoveOnBindUseCase.Outcome.Failed)
        val failed = outcome as MoveOnBindUseCase.Outcome.Failed
        assertEquals(listOf(7), failed.partiallyModifiedSpoolIds)
    }

    @Test
    fun `multi-source sweep moves uid off all owners on confirm`() = runTest {
        spoolman.nextFindSpoolsByCardUidResult =
            SpoolmanOutcome.Success(listOf(spool(7), spool(8)))
        spoolman.nextRemoveCardUidResult = SpoolmanOutcome.Success(spool(7))
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(spool(42))
        confirmer.nextResult = true

        val outcome = useCase.invoke(uid, targetSpoolId = 42)

        assertTrue("got $outcome", outcome is MoveOnBindUseCase.Outcome.Moved)
        val moved = outcome as MoveOnBindUseCase.Outcome.Moved
        assertEquals(listOf(7, 8), moved.fromSpoolIds)
        assertEquals(1, confirmer.confirmCalls)
        assertEquals(2, spoolman.removeCalls)
        assertEquals(1, spoolman.appendCalls)
        // Confirmer received both owners in its request payload.
        assertEquals(2, confirmer.lastRequest?.others?.size)
    }

    @Test
    fun `multi-source declined keeps everything as-is`() = runTest {
        spoolman.nextFindSpoolsByCardUidResult =
            SpoolmanOutcome.Success(listOf(spool(7), spool(8)))
        confirmer.nextResult = false

        val outcome = useCase.invoke(uid, targetSpoolId = 42)

        assertEquals(MoveOnBindUseCase.Outcome.Declined, outcome)
        assertEquals(0, spoolman.removeCalls)
        assertEquals(0, spoolman.appendCalls)
    }

    @Test
    fun `failed when findSpoolsByCardUid returns http error`() = runTest {
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.HttpError(503, "down")

        val outcome = useCase.invoke(uid, targetSpoolId = 42)

        assertTrue("got $outcome", outcome is MoveOnBindUseCase.Outcome.Failed)
        val failed = outcome as MoveOnBindUseCase.Outcome.Failed
        assertEquals(emptyList<Int>(), failed.partiallyModifiedSpoolIds)
    }
}
