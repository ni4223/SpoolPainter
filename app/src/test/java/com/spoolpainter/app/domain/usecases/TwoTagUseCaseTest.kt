package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.support.FakeMoveOnBindUseCase
import com.spoolpainter.app.support.FakeNfcRepository
import com.spoolpainter.app.support.FakeSpoolmanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TwoTagUseCaseTest {

    private val nfc = FakeNfcRepository()
    private val spoolman = FakeSpoolmanRepository()
    private val moveOnBind = FakeMoveOnBindUseCase()
    private val useCase = TwoTagUseCase(nfc, spoolman, moveOnBind)

    private val sampleUid = CardUid("AABBCCDD")
    private val sampleVendor = SpoolmanVendor(id = 7, name = "Bambu")
    private val sampleFilament = SpoolmanFilament(
        id = 11, name = "PLA Red", material = "PLA", vendor = sampleVendor,
        color_hex = "FF0000", settings_extruder_temp = 200, settings_bed_temp = 60,
    )
    private val sampleSpool = SpoolmanSpool(id = 42, filament = sampleFilament)

    private fun seedCaches() {
        spoolman.setSpools(listOf(sampleSpool))
        spoolman.setFilaments(listOf(sampleFilament))
        spoolman.setVendors(listOf(sampleVendor))
    }

    @Test
    fun `success writes identical payload and appends uid`() = runTest {
        seedCaches()
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(sampleSpool)
        nfc.queueArmResults(NfcResult.Success(sampleUid, TagClassification.Blank))

        val result = useCase.invoke(TwoTagInput(spoolId = 42))

        assertTrue("got $result", result is TwoTagResult.Success.SecondTagPaired)
        assertEquals(sampleUid, (result as TwoTagResult.Success.SecondTagPaired).uid)
        assertEquals(42, result.spoolId)
        assertEquals(1, spoolman.appendCalls)
        assertEquals(1, moveOnBind.invokeCalls)
    }

    @Test
    fun `vendor tag rejected no append`() = runTest {
        seedCaches()
        nfc.queueArmResults(
            NfcResult.Success(sampleUid, TagClassification.Vendor("non-OpenSpool NDEF")),
        )

        val result = useCase.invoke(TwoTagInput(spoolId = 42))

        assertTrue("got $result", result is TwoTagResult.VendorTagRejected)
        assertEquals(0, spoolman.appendCalls)
        assertEquals(0, moveOnBind.invokeCalls)
    }

    @Test
    fun `cache miss falls back to getSpool round trip`() = runTest {
        // Spools cache empty — invoke should call getSpool.
        spoolman.setFilaments(listOf(sampleFilament))
        spoolman.setVendors(listOf(sampleVendor))
        spoolman.nextGetSpoolResult = SpoolmanOutcome.Success(sampleSpool)
        spoolman.nextAppendCardUidResult = SpoolmanOutcome.Success(sampleSpool)
        nfc.queueArmResults(NfcResult.Success(sampleUid, TagClassification.Blank))

        val result = useCase.invoke(TwoTagInput(spoolId = 42))

        assertTrue("got $result", result is TwoTagResult.Success.SecondTagPaired)
        assertEquals(1, spoolman.getSpoolCalls)
    }

    @Test
    fun `move on bind declined returns cancelled`() = runTest {
        seedCaches()
        moveOnBind.nextOutcome = MoveOnBindUseCase.Outcome.Declined
        nfc.queueArmResults(NfcResult.Success(sampleUid, TagClassification.Blank))

        val result = useCase.invoke(TwoTagInput(spoolId = 42))

        assertTrue("got $result", result is TwoTagResult.Cancelled)
        assertEquals(0, spoolman.appendCalls)
    }

    @Test
    fun `verify failure returns VerifyFailed`() = runTest {
        seedCaches()
        nfc.queueArmResults(NfcResult.Error("verify mismatch (readback != written)"))

        val result = useCase.invoke(TwoTagInput(spoolId = 42))

        assertTrue("got $result", result is TwoTagResult.VerifyFailed)
        assertEquals(0, spoolman.appendCalls)
    }
}
