package com.spoolpainter.app.hardware.nfc

import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.emptyOpenSpoolRecords
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.jsonMimeRecords
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
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcRepositoryClassifierTest {

    private suspend fun classify(records: List<NdefRecordView>?): TagClassification {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), records)
        val repo = newRepository(wrapper = wrapper)
        repo.arm(NfcIntent.Read)
        repo.handleTag(makeTag())
        return (repo.state.value as NfcResult.Success).classification
    }

    @Test
    fun `null NDEF classifies as Blank`() = runTest {
        assertEquals(TagClassification.Blank, classify(null))
    }

    @Test
    fun `empty record list classifies as Blank (rewriteable)`() = runTest {
        // Tags with empty NDEF messages are typically half-formatted blanks
        // or our own writes that got interrupted. Treat them as Blank so the
        // user can rewrite them; the chip itself enforces lock state on
        // genuine factory-locked tags.
        assertEquals(TagClassification.Blank, classify(emptyList()))
    }

    @Test
    fun `application_vnd_openspool+json record classifies as OpenSpool`() = runTest {
        val payload = samplePayload()
        assertEquals(TagClassification.OpenSpool(payload), classify(openSpoolMimeRecords(payload)))
    }

    @Test
    fun `application_json record classifies as OpenSpool (forward-compat)`() = runTest {
        val payload = samplePayload()
        assertEquals(TagClassification.OpenSpool(payload), classify(jsonMimeRecords(payload)))
    }

    @Test
    fun `text_plain record classifies as Blank (overwritable)`() = runTest {
        // No OpenSpool MIME match. Could be anything from a URL bookmark to
        // a partial vendor write — but if the chip is rewriteable, we let
        // the user overwrite. The chip's own write protection is the gate.
        assertEquals(TagClassification.Blank, classify(textPlainRecords("not openspool")))
    }

    @Test
    fun `malformed JSON inside OpenSpool MIME classifies as Blank (recover from partial write)`() = runTest {
        // Most common cause: our own write got interrupted (app stopped,
        // tag lifted mid-write). Treat as Blank so the user can simply
        // re-tap and finish the write.
        assertEquals(
            TagClassification.Blank,
            classify(malformedOpenSpoolRecords("{not valid json")),
        )
    }

    @Test
    fun `non-OpenSpool JSON inside OpenSpool MIME classifies as Blank`() = runTest {
        assertEquals(
            TagClassification.Blank,
            classify(malformedOpenSpoolRecords("""{"foo":"bar"}""")),
        )
    }

    @Test
    fun `OpenSpool JSON missing required field classifies as Blank`() = runTest {
        val json = """{"protocol":"openspool","type":"PLA"}"""
        assertEquals(TagClassification.Blank, classify(malformedOpenSpoolRecords(json)))
    }

    @Test
    fun `empty payload bytes classify as Blank`() = runTest {
        assertEquals(TagClassification.Blank, classify(emptyOpenSpoolRecords()))
    }

    @Test
    fun `MIME type comparison is case-insensitive`() = runTest {
        val payload = samplePayload()
        val records = listOf(
            NdefRecordView(
                tnf = NdefRecordView.TNF_MIME_MEDIA,
                type = "Application/VND.OpenSpool+JSON".toByteArray(Charsets.US_ASCII),
                payload = com.spoolpainter.app.domain.primitives.OpenSpoolPayloadCodec
                    .toJson(payload).toByteArray(Charsets.UTF_8),
            ),
        )
        assertEquals(TagClassification.OpenSpool(payload), classify(records))
    }
}
