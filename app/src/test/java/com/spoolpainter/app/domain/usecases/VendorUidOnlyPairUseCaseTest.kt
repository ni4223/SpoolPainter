package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.support.FakeMoveOnBindUseCase
import com.spoolpainter.app.support.FakeSpoolmanRepository
import com.spoolpainter.app.ui.screens.main.FormState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VendorUidOnlyPairUseCaseTest {

    private val spoolman = FakeSpoolmanRepository()
    private val moveOnBind = FakeMoveOnBindUseCase()
    private val useCase = VendorUidOnlyPairUseCase(spoolman, moveOnBind)

    private val sampleUid = CardUid("AABBCCDD")
    private val sampleVendor = SpoolmanVendor(id = 7, name = "Bambu")
    private val sampleFilament = SpoolmanFilament(
        id = 11, name = "PLA Red", material = "PLA", vendor = sampleVendor,
        color_hex = "FF0000",
    )
    private val targetSpool = SpoolmanSpool(id = 42, filament = sampleFilament)

    private fun formInput(selectedSpoolId: Int? = null): VendorUidOnlyPairInput =
        VendorUidOnlyPairInput(
            form = FormState(
                material = Material("PLA", 190, 220, 55, 65),
                brand = Brand("Bambu"),
                colorHex = "FF0000",
                variant = "Matte",
                tempRanges = TempRanges(extruderMin = 200, extruderMax = 220),
                selectedSpoolId = selectedSpoolId,
                cardUid = sampleUid,
            ),
            newFilamentName = "Bambu PLA Matte",
            newFilamentVendor = "Bambu",
            resolvedMaterialName = "PLA",
            observedUid = sampleUid,
        )

    @Test
    fun `existing-spool happy path patches and returns Success`() = runTest {
        moveOnBind.nextOutcome = MoveOnBindUseCase.Outcome.Proceed
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(targetSpool)

        val result = useCase.invoke(formInput(selectedSpoolId = 42))

        assertTrue("got $result", result is VendorUidOnlyPairResult.Success.UidPaired)
        val s = result as VendorUidOnlyPairResult.Success.UidPaired
        assertEquals(42, s.spoolId)
        assertEquals(sampleUid, s.uid)
        assertEquals(false, s.isNewSpool)
        assertEquals(1, spoolman.appendCalls)
        assertEquals(0, spoolman.createSpoolCalls)
        assertEquals(1, moveOnBind.invokeCalls)
        assertEquals(42, moveOnBind.lastTargetSpoolId)
    }

    @Test
    fun `existing-spool path declined returns Cancelled`() = runTest {
        moveOnBind.nextOutcome = MoveOnBindUseCase.Outcome.Declined

        val result = useCase.invoke(formInput(selectedSpoolId = 42))

        assertTrue("got $result", result is VendorUidOnlyPairResult.Cancelled)
        assertEquals(0, spoolman.appendCalls)
    }

    @Test
    fun `existing-spool path append fails returns SpoolmanFailed`() = runTest {
        moveOnBind.nextOutcome = MoveOnBindUseCase.Outcome.Proceed
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.HttpError(500, "boom")

        val result = useCase.invoke(formInput(selectedSpoolId = 42))

        assertTrue("got $result", result is VendorUidOnlyPairResult.SpoolmanFailed)
    }

    @Test
    fun `new-spool happy path POST then PATCH returns Success isNewSpool=true`() = runTest {
        moveOnBind.nextOutcome = MoveOnBindUseCase.Outcome.Proceed
        spoolman.nextCreateSpoolResult = SpoolmanOutcome.Success(targetSpool)
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(targetSpool)

        val result = useCase.invoke(formInput(selectedSpoolId = null))

        assertTrue("got $result", result is VendorUidOnlyPairResult.Success.UidPaired)
        val s = result as VendorUidOnlyPairResult.Success.UidPaired
        assertEquals(42, s.spoolId)
        assertEquals(true, s.isNewSpool)
        assertEquals(1, spoolman.createSpoolCalls)
        assertEquals(1, spoolman.appendCalls)
        // Move-on-bind precheck for new-spool path uses sentinel target id.
        assertEquals(VendorUidOnlyPairUseCase.NEW_SPOOL_SENTINEL, moveOnBind.lastTargetSpoolId)
    }

    @Test
    fun `new-spool POST fail returns SpoolmanFailed`() = runTest {
        moveOnBind.nextOutcome = MoveOnBindUseCase.Outcome.Proceed
        spoolman.nextCreateSpoolResult = SpoolmanOutcome.HttpError(422, "missing density")

        val result = useCase.invoke(formInput(selectedSpoolId = null))

        assertTrue("got $result", result is VendorUidOnlyPairResult.SpoolmanFailed)
        assertEquals(0, spoolman.appendCalls)
    }

    @Test
    fun `move-on-bind partial commit propagates as MoveOnBindPartial`() = runTest {
        moveOnBind.nextOutcome = MoveOnBindUseCase.Outcome.Failed(
            reason = "PATCH B failed",
            partiallyModifiedSpoolIds = listOf(7),
        )

        val result = useCase.invoke(formInput(selectedSpoolId = 42))

        assertTrue("got $result", result is VendorUidOnlyPairResult.MoveOnBindPartial)
        val partial = result as VendorUidOnlyPairResult.MoveOnBindPartial
        assertEquals(7, partial.partiallyModifiedSpoolId)
        assertEquals(0, spoolman.appendCalls)
    }
}
