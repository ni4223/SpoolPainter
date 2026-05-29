package com.spoolpainter.app.hardware.nfc

import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.makeTag
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.newRepository
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.sampleUid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcRepositoryUidExtractionTest {

    @Test
    fun `wrapper read failure surfaces zero-length UID error`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateReadThrow(IllegalStateException("zero-length UID"))
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Read)
        repo.handleTag(makeTag())

        val err = repo.state.value as NfcResult.Error
        assertEquals("zero-length UID, non-NFC-A tag?", err.reason)
        assertTrue(err.cause is IllegalStateException)
    }

    @Test
    fun `Success carries canonical lowercase hex UID via fromBytes contract`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        // sampleUid is already canonical; rely on the U2 contract here.
        wrapper.simulateRead(sampleUid(), null)
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Read)
        repo.handleTag(makeTag())

        val state = repo.state.value as NfcResult.Success
        assertEquals(CardUid("04a1b2c3d4e580"), state.uid)
        assertEquals("04a1b2c3d4e580", state.uid.hex)
    }
}
