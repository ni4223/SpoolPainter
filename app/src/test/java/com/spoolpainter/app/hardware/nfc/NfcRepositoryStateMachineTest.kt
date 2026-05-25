package com.spoolpainter.app.hardware.nfc

import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.makeTag
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.newRepository
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.openSpoolMimeRecords
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.samplePayload
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.sampleUid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcRepositoryStateMachineTest {

    @Test
    fun `arm Read transitions Idle to Reading`() = runTest {
        val repo = newRepository()
        repo.arm(NfcIntent.Read)
        assertEquals(NfcResult.Reading, repo.state.value)
    }

    @Test
    fun `arm Write transitions Idle to Writing`() = runTest {
        val repo = newRepository()
        repo.arm(NfcIntent.Write(samplePayload()))
        assertEquals(NfcResult.Writing, repo.state.value)
    }

    @Test
    fun `arm Verify transitions Idle to Verifying`() = runTest {
        val repo = newRepository()
        repo.arm(NfcIntent.Verify(samplePayload()))
        assertEquals(NfcResult.Verifying, repo.state.value)
    }

    @Test
    fun `re-arm replaces prior intent`() = runTest {
        val repo = newRepository()
        repo.arm(NfcIntent.Read)
        repo.arm(NfcIntent.Write(samplePayload()))
        assertEquals(NfcResult.Writing, repo.state.value)
    }

    @Test
    fun `disarm from Reading returns to Idle`() = runTest {
        val repo = newRepository()
        repo.arm(NfcIntent.Read)
        repo.disarm()
        assertEquals(NfcResult.Idle, repo.state.value)
    }

    @Test
    fun `disarm from Idle is a no-op`() = runTest {
        val repo = newRepository()
        repo.disarm()
        assertEquals(NfcResult.Idle, repo.state.value)
    }

    @Test
    fun `disarm from terminal Success clears state`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        val payload = samplePayload()
        wrapper.simulateRead(sampleUid(), openSpoolMimeRecords(payload))
        val repo = newRepository(wrapper = wrapper)
        repo.arm(NfcIntent.Read)
        repo.handleTag(makeTag())
        assertTrue(repo.state.value is NfcResult.Success)
        repo.disarm()
        assertEquals(NfcResult.Idle, repo.state.value)
    }

    @Test
    fun `Reading then tap with OpenSpool tag yields Success(OpenSpool)`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        val payload = samplePayload()
        wrapper.simulateRead(sampleUid(), openSpoolMimeRecords(payload))
        val repo = newRepository(wrapper = wrapper)
        repo.arm(NfcIntent.Read)
        repo.handleTag(makeTag())
        val state = repo.state.value as NfcResult.Success
        assertEquals(sampleUid(), state.uid)
        assertEquals(TagClassification.OpenSpool(payload), state.classification)
    }

    @Test
    fun `Reading then tap with blank tag yields Success(Blank)`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), null)
        val repo = newRepository(wrapper = wrapper)
        repo.arm(NfcIntent.Read)
        repo.handleTag(makeTag())
        val state = repo.state.value as NfcResult.Success
        assertEquals(TagClassification.Blank, state.classification)
    }

    @Test
    fun `tap in Idle populates lastSeenTag without changing state`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), null)
        val repo = newRepository(wrapper = wrapper)
        repo.handleTag(makeTag())
        assertEquals(NfcResult.Idle, repo.state.value)
        assertEquals(sampleUid(), repo.lastSeenTag.value!!.uid)
    }
}
