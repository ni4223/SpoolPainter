package com.spoolpainter.app.hardware.nfc

import com.spoolpainter.app.domain.primitives.CardUid

data class RawTagRead(
    val uid: CardUid,
    val records: List<NdefRecordView>?,
    /** Tag.techList — used by classify to distinguish a non-NDEF vendor tag
     *  (e.g. MifareClassic-only) from a genuinely blank NDEF-formattable tag. */
    val techList: List<String> = emptyList(),
)
