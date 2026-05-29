package com.spoolpainter.app.hardware.nfc

import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.makeTag
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.malformedOpenSpoolRecords
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.newRepository
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.openSpoolMimeRecords
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.samplePayload
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.sampleUid
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.textPlainRecords
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NfcRepositoryWriteVerifyTest {

    @Test
    fun `write happy path produces Success(OpenSpool) after byte-equal readback`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        val payload = samplePayload()
        wrapper.simulateRead(sampleUid(), records = null)
        wrapper.simulateReadbackEchoesWritten()
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Write(payload))
        repo.handleTag(makeTag())

        val state = repo.state.value as NfcResult.Success
        assertEquals(TagClassification.OpenSpool(payload), state.classification)
        assertEquals(1, wrapper.writeCallCount)
    }

    @Test
    fun `write proceeds on text_plain tag (no software vendor block)`() = runTest {
        // Old behaviour: software classifier rejected non-OpenSpool NDEF as
        // "vendor". New behaviour: classifier returns Blank, write proceeds.
        // The chip's own write protection is the only gate — we do NOT
        // pre-block at the software layer (that misclassified our own
        // partial writes as vendor and blocked legitimate rewrites).
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), textPlainRecords("not openspool"))
        wrapper.simulateReadbackEchoesWritten()
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Write(samplePayload()))
        repo.handleTag(makeTag())

        val state = repo.state.value as NfcResult.Success
        assertEquals(TagClassification.OpenSpool(samplePayload()), state.classification)
        assertEquals(1, wrapper.writeCallCount)
    }

    @Test
    fun `write proceeds on malformed-OpenSpool tag (recover from prior partial write)`() = runTest {
        // A tag whose OpenSpool JSON didn't parse is most often our own
        // write that got interrupted. Allow re-writing instead of blocking.
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), malformedOpenSpoolRecords("{bad"))
        wrapper.simulateReadbackEchoesWritten()
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Write(samplePayload()))
        repo.handleTag(makeTag())

        val state = repo.state.value as NfcResult.Success
        assertEquals(1, wrapper.writeCallCount)
    }

    @Test
    fun `write with mismatched expected UID still writes (enforcement removed)`() = runTest {
        // expectedUid is no longer enforced — the legitimate two-tag flow
        // (Read tag 1, then Save & Write tag 2 to the same spool) needs the
        // write to accept whichever tag the user taps. Same-UID-on-two-spools
        // conflict is the job of MoveOnBindUseCase, not this layer.
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), records = null)
        wrapper.simulateReadbackEchoesWritten()
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Write(samplePayload(), expectedUid = CardUid("deadbeef")))
        repo.handleTag(makeTag())

        assertEquals(NfcResult.Success(sampleUid(), TagClassification.OpenSpool(samplePayload())), repo.state.value)
        assertEquals(1, wrapper.writeCallCount)
    }

    @Test
    fun `write with matching expected UID succeeds`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), records = null)
        wrapper.simulateReadbackEchoesWritten()
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Write(samplePayload(), expectedUid = sampleUid()))
        repo.handleTag(makeTag())

        assertTrue(repo.state.value is NfcResult.Success)
    }

    @Test
    fun `write throw surfaces write-failed error with cause`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), records = null)
        val cause = IOException("tag lost")
        wrapper.simulateWriteFailure(cause)
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Write(samplePayload()))
        repo.handleTag(makeTag())

        val err = repo.state.value as NfcResult.Error
        assertTrue("reason: ${err.reason}", err.reason.startsWith("write failed:"))
        assertEquals(cause, err.cause)
    }

    @Test
    fun `verify mismatch surfaces verify-mismatch error`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), records = null)
        wrapper.simulateReadback(textPlainRecords("garbage"))
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Write(samplePayload()))
        repo.handleTag(makeTag())

        val err = repo.state.value as NfcResult.Error
        assertEquals("verify mismatch (readback != written)", err.reason)
    }

    @Test
    fun `verify readback null treats write as success (NDEF-promoted tag)`() = runTest {
        // Fresh blank tags are promoted to NDEF format by the write itself.
        // The same Tag handle then can't be reattached for readback because
        // its captured tech list is pre-write. The bytes ARE on the tag;
        // surfacing a verify-mismatch would orphan a Spoolman spool every
        // time a user pairs a fresh blank.
        val uid = sampleUid()
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(uid, records = null)
        wrapper.simulateReadback(null)
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Write(samplePayload()))
        repo.handleTag(makeTag())

        assertEquals(
            NfcResult.Success(uid, com.spoolpainter.app.domain.primitives.TagClassification.OpenSpool(samplePayload())),
            repo.state.value,
        )
    }

    @Test
    fun `verify throw surfaces verify-mismatch error with cause`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), records = null)
        val cause = IOException("read lost")
        wrapper.simulateReadbackThrow(cause)
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Write(samplePayload()))
        repo.handleTag(makeTag())

        val err = repo.state.value as NfcResult.Error
        assertEquals("verify mismatch", err.reason)
        assertEquals(cause, err.cause)
    }

    @Test
    fun `write encodes MIME type as application_json (FR-U6b-Δ-3 Snapmaker U1 compat)`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), records = null)
        wrapper.simulateReadbackEchoesWritten()
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Write(samplePayload()))
        repo.handleTag(makeTag())

        val written = wrapper.lastWrittenRecords ?: error("no records written")
        assertEquals(1, written.size)
        val record = written.single()
        assertEquals(NdefRecordView.TNF_MIME_MEDIA, record.tnf)
        assertEquals(
            NfcRepository.MIME_JSON,
            String(record.type, Charsets.US_ASCII),
        )
    }

    @Test
    fun `two consecutive writes produce identical NDEF bytes (FR-6_2 two-tag invariant)`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        val payload = samplePayload()
        var capturedFirst: List<NdefRecordView>? = null

        wrapper.simulateRead(sampleUid(), records = null)
        wrapper.simulateReadbackEchoesWritten()
        val repo = newRepository(wrapper = wrapper)

        repo.arm(NfcIntent.Write(payload))
        repo.handleTag(makeTag())
        capturedFirst = (repo.state.value as NfcResult.Success).let { openSpoolMimeRecords(payload) }
        assertNotNull(capturedFirst)

        // Second tap (different UID, same payload) should produce identical bytes.
        wrapper.simulateRead(CardUid("aabbcc"), records = null)
        wrapper.simulateReadbackEchoesWritten()
        repo.arm(NfcIntent.Write(payload))
        repo.handleTag(makeTag())
        val capturedSecond = openSpoolMimeRecords(payload)

        assertEquals(capturedFirst, capturedSecond)
    }
}
