package com.spoolpainter.app.hardware.nfc.vendor

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MifareUltralight page reader tests. Mocks the static
 * `MifareUltralight.get(Tag)` factory + the resulting instance so we can
 * exercise the reader's loop in plain JVM tests. The reader requests four
 * pages per `readPages` call (16 bytes); chip-specific behaviour is the
 * responsibility of the platform driver, which we don't model here.
 */
class MifareUltralightReaderTest {

    @Before fun setUp() {
        mockkStatic(MifareUltralight::class)
    }

    @After fun tearDown() {
        unmockkStatic(MifareUltralight::class)
    }

    @Test fun `null when chip is not MifareUltralight`() {
        val tag = mockk<Tag>(relaxed = true)
        every { MifareUltralight.get(tag) } returns null
        assertNull(MifareUltralightReader.tryReadPages(tag))
    }

    @Test fun `full 36-page read returns 144 bytes`() {
        val tag = mockk<Tag>(relaxed = true)
        val mu = mockk<MifareUltralight>(relaxed = true)
        every { MifareUltralight.get(tag) } returns mu
        // Reader steps in 4-page increments (page = 0, 4, 8, ..., 32). 36/4 = 9 calls.
        every { mu.readPages(any()) } answers {
            val page = firstArg<Int>()
            ByteArray(16) { i -> ((page * 4 + i / 4) and 0xFF).toByte() }
        }
        val raw = MifareUltralightReader.tryReadPages(tag, pageCount = 36)
        assertNotNull(raw)
        assertEquals(36 * 4, raw!!.size)
    }

    @Test fun `short read truncates output`() {
        val tag = mockk<Tag>(relaxed = true)
        val mu = mockk<MifareUltralight>(relaxed = true)
        every { MifareUltralight.get(tag) } returns mu
        // First call succeeds with 16 bytes; second call throws (chip only
        // exposes 4 pages). Reader should return the 16 bytes it got.
        every { mu.readPages(0) } returns ByteArray(16) { 0x42 }
        every { mu.readPages(4) } throws java.io.IOException("end of pages")
        val raw = MifareUltralightReader.tryReadPages(tag, pageCount = 36)
        assertNotNull(raw)
        assertEquals(16, raw!!.size)
        assertEquals(0x42.toByte(), raw[0])
    }

    @Test fun `connect IOException returns null`() {
        val tag = mockk<Tag>(relaxed = true)
        val mu = mockk<MifareUltralight>(relaxed = true)
        every { MifareUltralight.get(tag) } returns mu
        every { mu.connect() } throws java.io.IOException("not connected")
        assertNull(MifareUltralightReader.tryReadPages(tag))
    }

    // runBlocking + a real 500ms timeout: deterministically after worker
    // start + register(), before the (hung) session could ever finish.
    @Test fun `bounded read force-closes a hung connect`() = runBlocking {
        // Marginal RF contact: connect() blocks like a stuck binder call
        // and only close() aborts it. The watchdog must trip, close the
        // tech, and surface a plain IOException.
        val tag = mockk<Tag>(relaxed = true)
        val mu = mockk<MifareUltralight>(relaxed = true)
        every { MifareUltralight.get(tag) } returns mu
        val unblock = CountDownLatch(1)
        every { mu.connect() } answers { unblock.await() }
        every { mu.close() } answers { unblock.countDown() }

        val thrown = runCatching {
            MifareUltralightReader.tryReadPagesBounded(
                tag,
                dispatcher = Dispatchers.Default,
                timeoutMs = 500L,
            )
        }.exceptionOrNull()

        assertNotNull("watchdog must throw on a hung session", thrown)
        assertTrue(
            "expected watchdog IOException, got $thrown",
            thrown is java.io.IOException && thrown.message!!.contains("watchdog"),
        )
        verify { mu.close() } // watchdog force-closed the hung tech
    }
}
