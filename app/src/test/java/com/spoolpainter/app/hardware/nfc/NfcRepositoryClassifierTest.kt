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
    fun `empty record list classifies as Vendor non-OpenSpool NDEF`() = runTest {
        val classification = classify(emptyList())
        assertTrue(classification is TagClassification.Vendor)
        assertEquals("non-OpenSpool NDEF", (classification as TagClassification.Vendor).reason)
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
    fun `text_plain record classifies as Vendor non-OpenSpool NDEF`() = runTest {
        val classification = classify(textPlainRecords("not openspool"))
        assertEquals(
            TagClassification.Vendor("non-OpenSpool NDEF"),
            classification,
        )
    }

    @Test
    fun `malformed JSON inside OpenSpool MIME classifies as Vendor`() = runTest {
        val classification = classify(malformedOpenSpoolRecords("{not valid json"))
        assertTrue(classification is TagClassification.Vendor)
        val reason = (classification as TagClassification.Vendor).reason
        assertTrue("reason is `$reason`", reason == "not OpenSpool JSON" || reason.startsWith("malformed JSON:"))
    }

    @Test
    fun `non-OpenSpool JSON inside OpenSpool MIME classifies as Vendor`() = runTest {
        val classification = classify(malformedOpenSpoolRecords("""{"foo":"bar"}"""))
        assertEquals(TagClassification.Vendor("not OpenSpool JSON"), classification)
    }

    @Test
    fun `OpenSpool JSON missing required field classifies as Vendor malformed`() = runTest {
        val json = """{"protocol":"openspool","type":"PLA"}"""
        val classification = classify(malformedOpenSpoolRecords(json))
        assertTrue(classification is TagClassification.Vendor)
        assertTrue(
            (classification as TagClassification.Vendor).reason.startsWith("malformed JSON:"),
        )
    }

    @Test
    fun `empty payload bytes classify as Vendor empty NDEF payload`() = runTest {
        val classification = classify(emptyOpenSpoolRecords())
        assertEquals(TagClassification.Vendor("empty NDEF payload"), classification)
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
