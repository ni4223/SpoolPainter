package com.spoolpainter.app.domain.primitives

sealed interface NfcResult {
    data object Idle : NfcResult
    data object Reading : NfcResult
    data object Writing : NfcResult
    data object Verifying : NfcResult
    data class Success(val uid: CardUid, val classification: TagClassification) : NfcResult
    data class Error(val reason: String, val cause: Throwable? = null) : NfcResult
}
