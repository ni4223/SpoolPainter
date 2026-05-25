package com.spoolpainter.app.hardware.nfc

import android.nfc.Tag
import com.spoolpainter.app.di.AppScope
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.OpenSpoolPayloadCodec
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

internal object NfcTestSupport {

    fun makeTag(): Tag = mockk(relaxed = true)

    fun samplePayload(spoolId: String? = "42"): OpenSpoolPayload =
        OpenSpoolPayload(
            type = "PLA",
            colorHex = "FFAA00",
            brand = "Polymaker",
            minTemp = "190",
            maxTemp = "220",
            bedMinTemp = "55",
            bedMaxTemp = "65",
            spoolId = spoolId,
        )

    fun openSpoolMimeRecords(payload: OpenSpoolPayload): List<NdefRecordView> = listOf(
        NdefRecordView(
            tnf = NdefRecordView.TNF_MIME_MEDIA,
            type = NfcRepository.MIME_OPENSPOOL.toByteArray(Charsets.US_ASCII),
            payload = OpenSpoolPayloadCodec.toJson(payload).toByteArray(Charsets.UTF_8),
        ),
    )

    fun jsonMimeRecords(payload: OpenSpoolPayload): List<NdefRecordView> = listOf(
        NdefRecordView(
            tnf = NdefRecordView.TNF_MIME_MEDIA,
            type = NfcRepository.MIME_JSON.toByteArray(Charsets.US_ASCII),
            payload = OpenSpoolPayloadCodec.toJson(payload).toByteArray(Charsets.UTF_8),
        ),
    )

    fun textPlainRecords(text: String): List<NdefRecordView> = listOf(
        NdefRecordView(
            tnf = NdefRecordView.TNF_MIME_MEDIA,
            type = "text/plain".toByteArray(Charsets.US_ASCII),
            payload = text.toByteArray(Charsets.UTF_8),
        ),
    )

    fun malformedOpenSpoolRecords(json: String): List<NdefRecordView> = listOf(
        NdefRecordView(
            tnf = NdefRecordView.TNF_MIME_MEDIA,
            type = NfcRepository.MIME_OPENSPOOL.toByteArray(Charsets.US_ASCII),
            payload = json.toByteArray(Charsets.UTF_8),
        ),
    )

    fun emptyOpenSpoolRecords(): List<NdefRecordView> = listOf(
        NdefRecordView(
            tnf = NdefRecordView.TNF_MIME_MEDIA,
            type = NfcRepository.MIME_OPENSPOOL.toByteArray(Charsets.US_ASCII),
            payload = ByteArray(0),
        ),
    )

    fun sampleUid(): CardUid = CardUid("04a1b2c3d4e580")

    @OptIn(ExperimentalCoroutinesApi::class)
    fun newRepository(
        wrapper: FakeNfcAdapterWrapper = FakeNfcAdapterWrapper(),
        clock: MutableClock = MutableClock(0L),
        ttlMs: Long = NfcRepository.TTL_MS_DEFAULT,
        scope: CoroutineScope = TestScope(UnconfinedTestDispatcher()),
    ): NfcRepository = NfcRepository(
        wrapper = wrapper,
        scope = scope,
        ioDispatcher = UnconfinedTestDispatcher(),
        clock = clock,
        ttlMs = ttlMs,
    )
}
