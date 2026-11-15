package com.spoolpainter.app.hardware.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import com.spoolpainter.app.hardware.nfc.vendor.VendorAuth
import com.spoolpainter.app.hardware.nfc.vendor.VendorId
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MifareClassicReader watchdog coverage: a connect() hung on marginal RF
 * contact (blocking binder call) must be force-closed by the watchdog
 * instead of wedging the OS NFC stack for the rest of the process
 * lifetime. Mirrors the MockK-on-tech pattern of MifareUltralightReaderTest.
 */
class MifareClassicReaderTest {

    @Before fun setUp() {
        mockkStatic(MifareClassic::class)
    }

    @After fun tearDown() {
        unmockkStatic(MifareClassic::class)
    }

    // runBlocking + a real 500ms timeout: deterministically after worker
    // start + register(), before the (hung) session could ever finish.
    @Test
    fun `bounded read force-closes a hung connect`() = runBlocking {
        val tag = mockk<Tag>(relaxed = true)
        val mc = mockk<MifareClassic>(relaxed = true)
        every { MifareClassic.get(tag) } returns mc
        val unblock = CountDownLatch(1)
        every { mc.connect() } answers { unblock.await() } // stuck binder call
        every { mc.close() } answers { unblock.countDown() } // close() aborts it

        val thrown = runCatching {
            MifareClassicReader.tryReadRawCountedMultiBounded(
                tag,
                vendorAuths = mapOf(VendorId.Bambu to VendorAuth(keysA = emptyList())),
                dispatcher = Dispatchers.Default,
                timeoutMs = 500L,
            )
        }.exceptionOrNull()

        assertNotNull("watchdog must throw on a hung session", thrown)
        assertTrue(
            "expected watchdog IOException, got $thrown",
            thrown is java.io.IOException && thrown.message!!.contains("watchdog"),
        )
        verify { mc.close() } // watchdog force-closed the hung tech
    }
}
