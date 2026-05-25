package com.spoolpainter.app.domain.primitives

import com.spoolpainter.app.domain.models.OpenSpoolPayload

sealed interface NfcIntent {
    data object Read : NfcIntent
    data class Write(
        val payload: OpenSpoolPayload,
        val expectedUid: CardUid? = null,
    ) : NfcIntent
    data class Verify(val expectedPayload: OpenSpoolPayload) : NfcIntent
}
