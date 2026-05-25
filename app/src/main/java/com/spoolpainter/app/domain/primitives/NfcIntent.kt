package com.spoolpainter.app.domain.primitives

// U1 ships only Read. Write(payload, expectedUid?) and Verify(expectedPayload)
// land in U4 once U2's OpenSpoolPayload + CardUid exist.
sealed interface NfcIntent {
    data object Read : NfcIntent
}
