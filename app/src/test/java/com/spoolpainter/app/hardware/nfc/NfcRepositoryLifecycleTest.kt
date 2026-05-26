package com.spoolpainter.app.hardware.nfc

import androidx.activity.ComponentActivity
import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.makeTag
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.newRepository
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.samplePayload
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.sampleUid
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcRepositoryLifecycleTest {

    @Test
    fun `attach with available adapter enables foreground dispatch`() {
        val wrapper = FakeNfcAdapterWrapper()
        val activity: ComponentActivity = mockk(relaxed = true)
        val repo = NfcTestSupport.newRepository(wrapper = wrapper)

        repo.attach(activity)
        assertSame(activity, wrapper.attachedActivity)
        assertEquals(1, wrapper.attachCount)
    }

    @Test
    fun `attach is idempotent for the same activity`() {
        val wrapper = FakeNfcAdapterWrapper()
        val activity: ComponentActivity = mockk(relaxed = true)
        val repo = NfcTestSupport.newRepository(wrapper = wrapper)

        repo.attach(activity)
        repo.attach(activity)
        assertEquals(1, wrapper.attachCount)
        assertEquals(0, wrapper.detachCount)
    }

    @Test
    fun `attach with different activity detaches the prior one`() {
        val wrapper = FakeNfcAdapterWrapper()
        val first: ComponentActivity = mockk(relaxed = true)
        val second: ComponentActivity = mockk(relaxed = true)
        val repo = NfcTestSupport.newRepository(wrapper = wrapper)

        repo.attach(first)
        repo.attach(second)
        assertEquals(2, wrapper.attachCount)
        assertEquals(1, wrapper.detachCount)
        assertSame(second, wrapper.attachedActivity)
    }

    @Test
    fun `detach disables foreground dispatch and is idempotent`() {
        val wrapper = FakeNfcAdapterWrapper()
        val activity: ComponentActivity = mockk(relaxed = true)
        val repo = NfcTestSupport.newRepository(wrapper = wrapper)

        repo.attach(activity)
        repo.detach()
        repo.detach()
        assertEquals(1, wrapper.detachCount)
        assertNull(wrapper.attachedActivity)
    }

    @Test
    fun `detach during Writing preserves Writing state`() = runTest {
        // Android 14+ singleTop activities can briefly onPause → onResume
        // around an NFC intent dispatch; we no longer surface a spurious
        // "paused mid-write" error in that case. The user-facing timeout
        // (withTimeoutOrNull in MainViewModel.onWriteTapped) catches a real
        // user-driven pause.
        val wrapper = FakeNfcAdapterWrapper()
        val activity: ComponentActivity = mockk(relaxed = true)
        val repo = newRepository(wrapper = wrapper)
        repo.attach(activity)
        repo.arm(NfcIntent.Write(samplePayload()))

        repo.detach()

        assertTrue(repo.state.value is NfcResult.Writing)
    }

    @Test
    fun `arm on no-adapter device emits NFC not available error`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.available = false
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Read)

        val state = repo.state.value as NfcResult.Error
        assertEquals("NFC not available", state.reason)
        assertTrue(state.cause == null)
    }

    @Test
    fun `tap dispatched while Idle still updates lastSeenTag`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), null)
        val repo = newRepository(wrapper = wrapper)

        repo.handleTag(makeTag())

        assertEquals(NfcResult.Idle, repo.state.value)
        assertEquals(sampleUid(), repo.lastSeenTag.value!!.uid)
    }
}
