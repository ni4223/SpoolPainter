package com.spoolpainter.app.hardware.nfc

import android.nfc.Tag
import androidx.activity.ComponentActivity
import com.spoolpainter.app.domain.primitives.CardUid
import kotlinx.coroutines.Dispatchers

internal class FakeNfcAdapterWrapper : NfcAdapterWrapper(adapter = null, dispatcher = Dispatchers.Unconfined) {

    var available: Boolean = true
    var attachedActivity: ComponentActivity? = null
    var attachCount: Int = 0
    var detachCount: Int = 0

    private var nextRead: () -> RawTagRead = { defaultRead }
    private var nextWriteThrowable: Throwable? = null
    private var nextReadback: () -> List<NdefRecordView>? = { lastWrittenRecords }

    var lastWrittenRecords: List<NdefRecordView>? = null
        private set
    var writeCallCount: Int = 0
        private set

    private val defaultRead: RawTagRead =
        RawTagRead(uid = CardUid("00"), records = null, techList = listOf("android.nfc.tech.NdefFormatable"))

    override fun isAvailable(): Boolean = available

    override fun enableForegroundDispatch(activity: ComponentActivity) {
        attachedActivity = activity
        attachCount++
    }

    override fun disableForegroundDispatch(activity: ComponentActivity) {
        if (attachedActivity === activity) {
            attachedActivity = null
        }
        detachCount++
    }

    override suspend fun read(tag: Tag): RawTagRead = nextRead()

    override suspend fun writeRecords(tag: Tag, records: List<NdefRecordView>) {
        writeCallCount++
        nextWriteThrowable?.let {
            nextWriteThrowable = null
            throw it
        }
        lastWrittenRecords = records
    }

    override suspend fun readRecords(tag: Tag): List<NdefRecordView>? = nextReadback()

    fun simulateRead(uid: CardUid, records: List<NdefRecordView>?) {
        // Tests use this for both blank-formattable and OpenSpool tags.
        // techList: NdefFormatable so a null records => Blank (formattable),
        // and a non-null records doesn't depend on techList.
        nextRead = { RawTagRead(uid, records, listOf("android.nfc.tech.NdefFormatable")) }
    }

    fun simulateReadThrow(throwable: Throwable) {
        nextRead = { throw throwable }
    }

    fun simulateWriteFailure(throwable: Throwable) {
        nextWriteThrowable = throwable
    }

    fun simulateReadbackThrow(throwable: Throwable) {
        nextReadback = { throw throwable }
    }

    fun simulateReadback(records: List<NdefRecordView>?) {
        nextReadback = { records }
    }

    fun simulateReadbackEchoesWritten() {
        nextReadback = { lastWrittenRecords }
    }
}
