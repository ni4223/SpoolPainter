package com.spoolpainter.app.hardware.nfc

import com.spoolpainter.app.domain.primitives.CardUid
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcRepositoryConsumeLastSeenTest {

    @Test
    fun `consume Read with fresh buffered tap returns Success and clears buffer`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        val clock = MutableClock(1_000L)
        wrapper.simulateRead(sampleUid(), null)
        val repo = newRepository(wrapper = wrapper, clock = clock)

        repo.handleTag(makeTag())
        assertNotNull(repo.lastSeenTag.value)

        clock.nowMs = 2_000L // within TTL (5_000 ms default)
        val result = repo.consumeLastSeen(NfcIntent.Read) as NfcResult.Success
        assertEquals(TagClassification.Blank, result.classification)
        assertNull(repo.lastSeenTag.value)
        assertTrue(repo.state.value is NfcResult.Success)
    }

    @Test
    fun `consume Read with expired buffered tap returns null without clearing`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        val clock = MutableClock(0L)
        wrapper.simulateRead(sampleUid(), null)
        val repo = newRepository(wrapper = wrapper, clock = clock)

        repo.handleTag(makeTag())
        clock.nowMs = NfcRepository.TTL_MS_DEFAULT + 1L
        val result = repo.consumeLastSeen(NfcIntent.Read)

        assertNull(result)
        assertNotNull(repo.lastSeenTag.value) // not cleared on expiry
    }

    @Test
    fun `consume Read with no buffer returns null`() = runTest {
        val repo = newRepository()
        assertNull(repo.consumeLastSeen(NfcIntent.Read))
    }

    @Test
    fun `consume Read returns null when state is not Idle`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), null)
        val repo = newRepository(wrapper = wrapper)

        repo.handleTag(makeTag())
        repo.arm(NfcIntent.Read)

        val result = repo.consumeLastSeen(NfcIntent.Read)
        assertNull(result)
    }

    @Test
    fun `consume Write always returns null`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), null)
        val repo = newRepository(wrapper = wrapper)

        repo.handleTag(makeTag())
        val result = repo.consumeLastSeen(NfcIntent.Write(samplePayload()))
        assertNull(result)
        assertNotNull(repo.lastSeenTag.value)
    }

    @Test
    fun `consume Verify always returns null`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), null)
        val repo = newRepository(wrapper = wrapper)

        repo.handleTag(makeTag())
        val result = repo.consumeLastSeen(NfcIntent.Verify(samplePayload()))
        assertNull(result)
    }

    @Test
    fun `multiple taps in Idle overwrite the buffer (latest wins)`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        val first = CardUid("0001")
        val second = CardUid("0002")
        wrapper.simulateRead(first, openSpoolMimeRecords(samplePayload()))
        val repo = newRepository(wrapper = wrapper)
        repo.handleTag(makeTag())

        wrapper.simulateRead(second, null)
        repo.handleTag(makeTag())

        assertEquals(second, repo.lastSeenTag.value!!.uid)
        assertEquals(TagClassification.Blank, repo.lastSeenTag.value!!.classification)
    }
}
