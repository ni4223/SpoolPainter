package com.spoolpainter.app.domain.primitives

// U1 ships only the steady-state cases. Success(uid, classification) and
// Error(reason, cause?) land in U4 once U2's CardUid + TagClassification exist.
sealed interface NfcResult {
    data object Idle : NfcResult
    data object Reading : NfcResult
    data object Writing : NfcResult
    data object Verifying : NfcResult
}
