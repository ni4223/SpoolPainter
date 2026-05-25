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
        RawTagRead(uid, records)
    }

    open suspend fun writeRecords(tag: Tag, records: List<NdefRecordView>) = withContext(dispatcher) {
        val ndef = Ndef.get(tag)
            ?: throw IllegalStateException("tag does not support NDEF")
        try {
            ndef.connect()
            ndef.writeNdefMessage(records.toNdefMessage())
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
