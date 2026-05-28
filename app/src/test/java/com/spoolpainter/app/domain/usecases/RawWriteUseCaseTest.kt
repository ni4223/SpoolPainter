package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.hardware.nfc.TagBuffer
import com.spoolpainter.app.support.FakeNfcRepository
import com.spoolpainter.app.ui.screens.main.FormState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RawWriteUseCaseTest {

    private val nfc = FakeNfcRepository()
    private val useCase = RawWriteUseCase(nfc)

    private val sampleUid = CardUid("AABBCCDD")

    private fun input(): RawWriteInput = RawWriteInput(
        form = FormState(
            material = Material("PLA", 190, 220, 55, 65),
            brand = Brand("Bambu"),
            colorHex = "FF0000",
            variant = "Matte",
            tempRanges = TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60),
        ),
    )

    @Test
    fun `happy path writes and returns Success`() = runTest {
        nfc.queueArmResults(NfcResult.Success(sampleUid, TagClassification.Blank))

        val result = useCase.invoke(input())

        assertTrue("got $result", result is RawWriteResult.Success.Written)
        assertEquals(sampleUid, (result as RawWriteResult.Success.Written).uid)
        assertEquals(1, nfc.armCalls)
    }

    @Test
    fun `payload omits spool_id`() = runTest {
        nfc.queueArmResults(NfcResult.Success(sampleUid, TagClassification.Blank))

        useCase.invoke(input())

        val intent = nfc.lastArmedIntent
        assertTrue(intent is NfcIntent.Write)
        val payload = (intent as NfcIntent.Write).payload
        assertNull("raw-write payload must omit spool_id", payload.spoolId)
    }

    @Test
    fun `vendor tag protected returns VendorTagRejected`() = runTest {
        nfc.pushLastSeenTag(
            TagBuffer(sampleUid, TagClassification.Vendor("non-NDEF tag"), capturedAtEpochMs = 0L),
        )
        nfc.queueArmResults(NfcResult.Error("vendor-tag protected (FR-4.7)"))

        val result = useCase.invoke(input())

        assertTrue("got $result", result is RawWriteResult.VendorTagRejected)
        assertEquals(sampleUid, (result as RawWriteResult.VendorTagRejected).uid)
    }

    @Test
    fun `verify mismatch returns VerifyFailed`() = runTest {
        nfc.pushLastSeenTag(
            TagBuffer(sampleUid, TagClassification.Blank, capturedAtEpochMs = 0L),
        )
        nfc.queueArmResults(NfcResult.Error("verify mismatch: bytes differ"))

        val result = useCase.invoke(input())

        assertTrue("got $result", result is RawWriteResult.VerifyFailed)
        val verify = result as RawWriteResult.VerifyFailed
        assertEquals(sampleUid, verify.uid)
    }

    @Test
    fun `generic NFC error returns NfcFailed`() = runTest {
        nfc.queueArmResults(NfcResult.Error("Ndef.writeNdefMessage IOException"))

        val result = useCase.invoke(input())

        assertTrue("got $result", result is RawWriteResult.NfcFailed)
        val failed = result as RawWriteResult.NfcFailed
        assertEquals("Ndef.writeNdefMessage IOException", failed.reason)
    }

    @Test
    fun `zero length uid returns NfcFailed`() = runTest {
        nfc.queueArmResults(NfcResult.Success(CardUid(""), TagClassification.Blank))

        val result = useCase.invoke(input())

        assertTrue("got $result", result is RawWriteResult.NfcFailed)
    }
}
