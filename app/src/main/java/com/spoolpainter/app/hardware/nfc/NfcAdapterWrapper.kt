package com.spoolpainter.app.hardware.nfc

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import androidx.activity.ComponentActivity
import com.spoolpainter.app.di.IoDispatcher
import com.spoolpainter.app.domain.primitives.CardUid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class NfcAdapterWrapper @Inject constructor(
    private val adapter: NfcAdapter?,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    open fun isAvailable(): Boolean = adapter?.isEnabled == true

    open fun enableForegroundDispatch(activity: ComponentActivity) {
        val nfc = adapter ?: return
        if (!nfc.isEnabled) return
        val intent = Intent(activity, activity.javaClass)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            activity,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE,
        )
        nfc.enableForegroundDispatch(activity, pending, null, null)
    }

    open fun disableForegroundDispatch(activity: ComponentActivity) {
        val nfc = adapter ?: return
        if (!nfc.isEnabled) return
        nfc.disableForegroundDispatch(activity)
    }

    open suspend fun read(tag: Tag): RawTagRead = withContext(dispatcher) {
        val uid = CardUid.fromBytes(tag.id)
        val records = readRecordsBlocking(tag)
        RawTagRead(uid, records, tag.techList?.toList().orEmpty())
    }

    open suspend fun writeRecords(tag: Tag, records: List<NdefRecordView>) = withContext(dispatcher) {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                writeViaNdef(ndef, records)
                return@withContext
            } catch (e: java.io.IOException) {
                // Ndef.writeNdefMessage failed. If the tag also exposes
                // NdefFormatable, fall through to a fresh format pass —
                // recovers tags whose capability container ended up in an
                // inconsistent state from a previous interrupted write.
                // v1's simpler write path bulldozed past this state on every
                // attempt; v2 is more careful and so needs a deliberate
                // recovery hop.
                val formatable = android.nfc.tech.NdefFormatable.get(tag) ?: throw e
                writeViaFormatable(formatable, records)
                return@withContext
            }
        }
        // Ndef.get returned null. Two cases:
        //   1. Truly non-NDEF tag (factory-locked vendor) → throw NonNdef.
        //   2. Fresh blank that Android hasn't promoted yet, OR a stale Tag
        //      handle from a re-tap right after our previous write — both
        //      surface as NdefFormatable in the techList. Format + write.
        val formatable = android.nfc.tech.NdefFormatable.get(tag)
            ?: throw NonNdefTagException()
        writeViaFormatable(formatable, records)
    }

    private fun writeViaFormatable(
        formatable: android.nfc.tech.NdefFormatable,
        records: List<NdefRecordView>,
    ) {
        try {
            formatable.connect()
            val message = records.toNdefMessage()
            val payloadSize = message.byteArrayLength
            try {
                formatable.format(message)
            } catch (e: java.io.IOException) {
                throw java.io.IOException(
                    "NdefFormatable.format IOException (payload=${payloadSize}B): ${e.message ?: "no message"}",
                    e,
                )
            }
        } finally {
            try {
                formatable.close()
            } catch (_: Throwable) {
                // best-effort close
            }
        }
    }

    private fun writeViaNdef(ndef: Ndef, records: List<NdefRecordView>) {
        try {
            ndef.connect()
            val message = records.toNdefMessage()
            val payloadSize = message.byteArrayLength
            // No pre-flight isWritable / maxSize round-trips. Each is a
            // separate NfcA transceive on the capability container; on
            // marginal taps those extra read cycles leave the chip in a
            // state where the subsequent writeNdefMessage fails with a
            // generic IOException. v1 wrote straight through and was
            // robust precisely because it didn't pre-check.
            // On failure, fall back to a single capacity probe so the user
            // gets a useful message when the tag really IS too small
            // (NTAG213's 144 B vs our ~216 B payload).
            try {
                ndef.writeNdefMessage(message)
            } catch (e: java.io.IOException) {
                val capacityMessage = runCatching { ndef.maxSize }
                    .getOrNull()
                    ?.takeIf { it < payloadSize }
                    ?.let { cap -> "tag too small: payload ${payloadSize}B > capacity ${cap}B" }
                throw java.io.IOException(
                    capacityMessage ?: "Ndef.writeNdefMessage IOException (payload=${payloadSize}B): ${e.message ?: "no message"}",
                    e,
                )
            }
        } finally {
            try {
                ndef.close()
            } catch (_: Throwable) {
                // best-effort close
            }
        }
    }

    open suspend fun readRecords(tag: Tag): List<NdefRecordView>? = withContext(dispatcher) {
        readRecordsBlocking(tag)
    }

    private fun readRecordsBlocking(tag: Tag): List<NdefRecordView>? {
        val ndef = Ndef.get(tag) ?: return null
        return try {
            ndef.connect()
            ndef.ndefMessage?.records?.map { it.toView() }
        } finally {
            try {
                ndef.close()
            } catch (_: Throwable) {
                // best-effort close
            }
        }
    }

    private fun List<NdefRecordView>.toNdefMessage(): NdefMessage {
        val records = map { view ->
            NdefRecord(view.tnf, view.type, ByteArray(0), view.payload)
        }.toTypedArray()
        return NdefMessage(records)
    }

    private fun NdefRecord.toView(): NdefRecordView =
        NdefRecordView(tnf = tnf, type = type ?: ByteArray(0), payload = payload ?: ByteArray(0))
}

/**
 * Thrown when [NfcAdapterWrapper.writeRecords] encounters a tag that does not
 * expose the NDEF tech — typically a factory-locked vendor tag. NfcRepository
 * maps this to the standard vendor-tag error so the UI surfaces "Vendor tag —
 * write blocked" rather than "tag does not support NDEF" (UI-09).
 */
class NonNdefTagException : IllegalStateException("non-NDEF tag (vendor)")
