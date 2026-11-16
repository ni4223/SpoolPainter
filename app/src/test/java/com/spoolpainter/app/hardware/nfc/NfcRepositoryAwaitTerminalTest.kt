package com.spoolpainter.app.hardware.nfc

import android.nfc.Tag
import androidx.activity.ComponentActivity
import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * awaitTerminal contract (phase-aware wait introduced to stop the outer
 * withTimeoutOrNull from cancelling use cases mid-write):
 *  (a) no tap within tapWaitMs → null;
 *  (b) tap consumed then terminal within the I/O grace → the terminal result;
 *  (c) tap consumed but terminal never arrives within a short ioGraceMs → null.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NfcRepositoryAwaitTerminalTest {

    @Test
    fun `no tap within tap wait returns null`() = runTest {
        val repo = NfcTestSupport.newRepository()
        repo.arm(NfcIntent.Write(NfcTestSupport.samplePayload()))

        val result = repo.awaitTerminal(tapWaitMs = 1_000L)

        assertNull("expected null when nobody taps, got $result", result)
    }

    @Test
    fun `tap consumed then terminal within grace returns terminal result`() = runTest {
        val repo = NfcTestSupport.newRepository()
        repo.arm(NfcIntent.Write(NfcTestSupport.samplePayload()))

        // Start the waiter BEFORE the tap so the test exercises the real
        // interleaving: tap-wait suspends → tap consumed (signal completes
        // while state is still Writing) → I/O-grace waits for the write to
        // transition the state to terminal.
        val waiter = async(UnconfinedTestDispatcher(testScheduler)) {
            repo.awaitTerminal(tapWaitMs = 1_000L, ioGraceMs = 5_000L)
        }
        repo.handleTag(NfcTestSupport.makeTag())

        val result = waiter.await()
        assertTrue("expected Success, got $result", result is NfcResult.Success)
        assertEquals(NfcTestSupport.sampleUid(), (result as NfcResult.Success).uid)
    }

    @Test
    fun `tap consumed but terminal stuck past io grace returns null`() = runTest {
        // writeRecords never returns (virtual-time "forever"): the tap is
        // consumed but no terminal state is ever produced.
        val repo = NfcTestSupport.newRepository(wrapper = HangingWriteWrapper())
        repo.arm(NfcIntent.Write(NfcTestSupport.samplePayload()))
        val tapJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.handleTag(NfcTestSupport.makeTag())
        }

        val result = repo.awaitTerminal(tapWaitMs = 1_000L, ioGraceMs = 100L)

        assertNull("expected null when I/O never terminates, got $result", result)
        // The tap WAS consumed (not a no-tap timeout): the arm is gone and
        // the state is still stuck mid-write.
        assertTrue(repo.state.value is NfcResult.Writing)
        tapJob.cancel()
    }

    @Test
    fun `stuck io past grace re-registers foreground dispatch as self-heal`() = runTest {
        // Phase-2 blowout with an attached activity: the wedged tech
        // connection can block the OEM NFC dispatch path for the whole
        // process, so the repository must hammer the stack back to health
        // by disabling and re-enabling the foreground dispatch.
        val wrapper = HangingWriteWrapper()
        val activity: ComponentActivity = mockk(relaxed = true)
        val repo = NfcTestSupport.newRepository(wrapper = wrapper)
        repo.attach(activity)
        assertEquals(1, wrapper.attachCount)
        repo.arm(NfcIntent.Write(NfcTestSupport.samplePayload()))
        val tapJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.handleTag(NfcTestSupport.makeTag())
        }

        val result = repo.awaitTerminal(tapWaitMs = 1_000L, ioGraceMs = 100L)

        assertNull("expected null when I/O never terminates, got $result", result)
        // enableForegroundDispatch fired once at attach and once more from
        // the recovery hammer (disable → enable); the activity stays
        // attached (not detached).
        assertEquals("foreground dispatch must have been re-enabled", 2, wrapper.attachCount)
        assertEquals("recovery disables exactly once before re-enabling", 1, wrapper.detachCount)
        tapJob.cancel()
    }

    @Test
    fun `already terminal state returns immediately without waiting for a tap`() = runTest {
        val repo = NfcTestSupport.newRepository(wrapper = FakeNfcAdapterWrapper().apply { available = false })
        repo.arm(NfcIntent.Read) // NFC unavailable → arm errors out synchronously

        val result = repo.awaitTerminal(tapWaitMs = 1_000L)

        assertTrue("expected Error, got $result", result is NfcResult.Error)
        assertEquals("NFC not available", (result as NfcResult.Error).reason)
    }

    /** Write hangs forever (virtual time) — the pathological stuck-I/O case. */
    private class HangingWriteWrapper : FakeNfcAdapterWrapper() {
        override suspend fun writeRecords(tag: Tag, records: List<NdefRecordView>) {
            delay(60_000L)
        }
    }
}
