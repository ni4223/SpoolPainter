package com.spoolpainter.app.hardware.nfc

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.usecases.CreateAndPairInput
import com.spoolpainter.app.domain.usecases.CreateAndPairResult
import com.spoolpainter.app.domain.usecases.CreateAndPairUseCase
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.makeTag
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.newRepository
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.samplePayload
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.sampleUid
import com.spoolpainter.app.support.FakeSpoolmanRepository
import com.spoolpainter.app.support.MoveOnBindNoOp
import com.spoolpainter.app.ui.screens.main.FormState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Regression for the maintainer fix on PR #15 (Layer 1 restructure):
 * NfcRepository clears the lastSeenTag buffer on write-path terminal
 * transitions (ghost re-consume fix), and NfcResult.Error carries the tag's
 * UID so CreateAndPairUseCase / TwoTagUseCase / RawWriteUseCase — which used
 * to read the UID from that buffer after an Error — can still pair the tag
 * with Spoolman on a failed write.
 *
 * Runs against the REAL [NfcRepository] (not a fake repository); only the
 * OS adapter is faked, and the tag surfaces with a realistic NTAG techList
 * (NfcA + MifareUltralight + Ndef), as observed on actual NTAG213/215 chips.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NfcRepositoryErrorUidPairingTest {

    /** What an Android stack reports for an NTAG-family chip. */
    private val ntagTechList = listOf(
        "android.nfc.tech.NfcA",
        "android.nfc.tech.MifareUltralight",
        "android.nfc.tech.Ndef",
    )

    @Test
    fun `failed NTAG write Error carries the UID and clears the buffer`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        val clock = MutableClock(1_000L)
        wrapper.simulateWriteFailure(IOException("tag lost mid-write"))
        val repo = newRepository(wrapper = wrapper, clock = clock)

        repo.arm(NfcIntent.Write(samplePayload()))
        repo.handleTag(makeTag(techList = ntagTechList))

        val error = repo.state.value as NfcResult.Error
        assertTrue("got ${error.reason}", error.reason.startsWith("write failed"))
        assertEquals(sampleUid(), error.uid)

        // The spent write tap must not linger: a Read pressed within the 5s
        // TTL (the classic ghost re-consume window) finds nothing.
        assertNull(repo.lastSeenTag.value)
        clock.nowMs = 2_000L
        assertNull(repo.consumeLastSeen(NfcIntent.Read))
    }

    @Test
    fun `vendor-blocked NTAG write Error carries the UID and clears the buffer`() = runTest {
        // Same contract on the vendor pre-block transition: an NTAG-style
        // Ultralight chip with Ndef auto-promoted can still be vendor-classed
        // (no readable NDEF) — the rejection must carry the UID for the
        // Spoolman UID-only pair fallback.
        val wrapper = FakeNfcAdapterWrapper()
        val clock = MutableClock(1_000L)
        // No Ndef in the techList → the Writing-state classifier (which
        // classifies from tag.techList directly, no NDEF pre-read) lands the
        // chip in the Vendor branch while keeping the realistic NfcA +
        // MifareUltralight prefix.
        val vendorTechList = ntagTechList - "android.nfc.tech.Ndef"
        val repo = newRepository(wrapper = wrapper, clock = clock)

        repo.arm(NfcIntent.Write(samplePayload()))
        repo.handleTag(makeTag(techList = vendorTechList))

        val error = repo.state.value as NfcResult.Error
        assertTrue("got ${error.reason}", error.reason.contains("vendor-tag"))
        assertEquals(sampleUid(), error.uid)
        assertNull(repo.lastSeenTag.value)
        clock.nowMs = 2_000L
        assertNull(repo.consumeLastSeen(NfcIntent.Read))
    }

    @Test
    fun `failed NTAG write still pairs via CreateAndPairUseCase reading the Error UID`() =
        runTest(UnconfinedTestDispatcher()) {
            val wrapper = FakeNfcAdapterWrapper()
            val clock = MutableClock(1_000L)
            wrapper.simulateWriteFailure(IOException("tag lost mid-write"))
            val repo = newRepository(wrapper = wrapper, clock = clock)
            val spoolman = FakeSpoolmanRepository()
            spoolman.nextAppendCardUidResult =
                SpoolmanOutcome.Success(SpoolmanSpool(id = 42, filament = SpoolmanFilament(id = 7, material = "PLA")))
            val useCase = CreateAndPairUseCase(repo, spoolman, MoveOnBindNoOp)
            val input = CreateAndPairInput(
                spoolId = 42,
                isNewSpool = false,
                form = FormState(),
                newFilamentName = "PLA Red",
                newFilamentVendor = "Bambu",
            )

            // The use case arms its own Write and parks on the terminal
            // state; the physical NTAG tap then fails mid-write.
            var result: CreateAndPairResult? = null
            launch { result = useCase.invoke(input) }
            repo.handleTag(makeTag(techList = ntagTechList))

            // Repository contract: Error carries the UID, buffer is spent.
            val error = repo.state.value as NfcResult.Error
            assertEquals(sampleUid(), error.uid)
            assertNull(repo.lastSeenTag.value)

            // Use-case contract: the failed write still surfaces the UID and
            // the spool↔UID pairing was committed to Spoolman — the exact
            // regression the buffer clearing would have broken without the
            // UID riding in the Error.
            val failed = result as CreateAndPairResult.NfcFailed
            assertEquals(sampleUid(), failed.uid)
            assertTrue("got ${failed.reason}", failed.reason.startsWith("write failed"))
            assertEquals(1, spoolman.appendCalls)
            assertEquals(42, spoolman.lastAppend?.first)
            assertEquals(sampleUid(), spoolman.lastAppend?.second)
        }
}
