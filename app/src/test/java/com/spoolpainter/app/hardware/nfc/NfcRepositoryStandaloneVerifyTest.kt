package com.spoolpainter.app.hardware.nfc

import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.makeTag
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.newRepository
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.openSpoolMimeRecords
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.samplePayload
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.sampleUid
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.textPlainRecords
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcRepositoryStandaloneVerifyTest {

    @Test
    fun `arm Verify happy path returns Success when readback matches`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        val payload = samplePayload()
        wrapper.simulateRead(sampleUid(), openSpoolMimeRecords(payload))
        wrapper.simulateReadback(openSpoolMimeRecords(payload))
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Verify(payload))
        repo.handleTag(makeTag())

        val state = repo.state.value as NfcResult.Success
        assertEquals(TagClassification.OpenSpool(payload), state.classification)
    }

    @Test
    fun `arm Verify mismatch returns Error verify-mismatch`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        val payload = samplePayload()
        wrapper.simulateRead(sampleUid(), openSpoolMimeRecords(payload))
        wrapper.simulateReadback(textPlainRecords("garbage"))
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Verify(payload))
        repo.handleTag(makeTag())

        val state = repo.state.value as NfcResult.Error
        assertEquals("verify mismatch", state.reason)
    }

    @Test
    fun `arm Verify against vendor-classified tag returns vendor-protected error`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), textPlainRecords("vendor"))
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Verify(samplePayload()))
        repo.handleTag(makeTag())

        val state = repo.state.value as NfcResult.Error
        assertTrue(state.reason.startsWith("vendor-tag protected (FR-4.7)"))
    }

    @Test
    fun `arm Verify with null readback returns verify-mismatch`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), openSpoolMimeRecords(samplePayload()))
        wrapper.simulateReadback(null)
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Verify(samplePayload()))
        repo.handleTag(makeTag())

        val err = repo.state.value as NfcResult.Error
        assertEquals("verify mismatch", err.reason)
    }
}
