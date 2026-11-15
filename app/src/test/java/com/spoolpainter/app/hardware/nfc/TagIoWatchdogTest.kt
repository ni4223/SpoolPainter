package com.spoolpainter.app.hardware.nfc

import android.nfc.Tag
import android.nfc.tech.TagTechnology
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tag I/O watchdog helper (hung-`connect()` recovery for marginal RF
 * contact). The helper is what matters here: it races a blocking session
 * against a timeout, force-closes the registered tech on trip, and reports
 * a plain IOException so repository catch paths treat it as a normal
 * read/write failure.
 */
class TagIoWatchdogTest {

    /**
     * Models a real hung binder connect: blocks until close() is called
     * and ignores thread interruption — a blocking binder call into the OS
     * NFC service doesn't see Java interrupts; only close() aborts it.
     */
    private class HangingTech : TagTechnology {
        var closeCount = 0
        private val unblock = CountDownLatch(1)

        override fun connect() {
            try {
                unblock.await()
            } catch (_: InterruptedException) {
                // Swallowed on purpose: interruption must NOT unblock the
                // session, only close() may.
            }
        }

        override fun close() {
            closeCount++
            unblock.countDown()
        }

        override fun isConnected(): Boolean = false

        override fun getTag(): Tag? = null
    }

    // NOTE: runBlocking + real (short) timeouts throughout. Under runTest's
    // virtual clock the timeout can fire before the Default worker has even
    // started the block (register() not yet executed → nothing to close),
    // and a suspension on a real worker thread makes the scheduler
    // fast-forward straight into pending timeouts. Real time makes the
    // register-before-timeout ordering deterministic.

    @Test
    fun `hung session times out with IOException and force-closes the tech`() = runBlocking {
        val tech = HangingTech()

        val thrown = runCatching {
            withTechWatchdog(Dispatchers.Default, timeoutMs = 500L) { closer ->
                closer.register(tech)
                tech.connect() // hangs until the watchdog closes it
                "unreachable"
            }
        }.exceptionOrNull()

        assertNotNull("watchdog must throw on timeout", thrown)
        assertTrue(
            "must be an IOException so repository catch paths handle it, got $thrown",
            thrown is java.io.IOException,
        )
        assertTrue(
            "message should name the watchdog: ${thrown!!.message}",
            thrown.message!!.contains("watchdog"),
        )
        assertEquals("watchdog must have force-closed the hung tech", 1, tech.closeCount)
    }

    // runBlocking (real time) for the happy paths: under runTest's virtual
    // clock, a suspension on a real Dispatchers.Default thread makes the
    // scheduler fast-forward straight into the pending timeout, which
    // would spuriously trip the watchdog under test.

    @Test
    fun `session completing within the budget returns normally without closing`() = runBlocking {
        val tech = HangingTech()

        val result = withTechWatchdog(Dispatchers.Default, timeoutMs = 60_000L) { closer ->
            closer.register(tech)
            "ok"
        }

        assertEquals("ok", result)
        assertEquals("no watchdog close when the session behaved", 0, tech.closeCount)
    }

    @Test
    fun `null result within the budget is returned, not mistaken for a timeout`() = runBlocking {
        // Short vendor reads legitimately return null; the watchdog must
        // not conflate that with its own timeout signal.
        val result = withTechWatchdog<String?>(Dispatchers.Default, timeoutMs = 60_000L) { null }

        assertNull(result)
    }
}
