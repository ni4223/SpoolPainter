package com.spoolpainter.app.hardware.nfc

import com.spoolpainter.app.domain.primitives.CardUid

data class RawTagRead(
    val uid: CardUid,
    val records: List<NdefRecordView>?,
)
