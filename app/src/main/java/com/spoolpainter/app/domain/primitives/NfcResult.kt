package com.spoolpainter.app.domain.primitives

sealed interface NfcResult {
    data object Idle : NfcResult
    data object Reading : NfcResult
    data object Writing : NfcResult
    data object Verifying : NfcResult
    data class Success(val uid: CardUid, val classification: TagClassification) : NfcResult

    /**
     * Terminal failure. [uid] carries the UID of the tag the failing session
     * was working on, when it had one — the write path clears the lastSeenTag
     * buffer on its terminal transitions (so a Read within the 5s TTL can't
     * ghost-re-consume the just-written tag), which means the buffer is no
     * longer a reliable UID source after an Error. Null when the session
     * never surfaced a UID (e.g. a read/decode failure before classification).
     */
    data class Error(val reason: String, val cause: Throwable? = null, val uid: CardUid? = null) : NfcResult
}
