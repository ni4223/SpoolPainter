package com.spoolpainter.app.hardware.nfc

import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.TagClassification

data class TagBuffer(
    val uid: CardUid,
    val classification: TagClassification,
    val capturedAtEpochMs: Long,
)
