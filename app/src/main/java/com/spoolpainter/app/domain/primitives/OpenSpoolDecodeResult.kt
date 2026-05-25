package com.spoolpainter.app.domain.primitives

import com.spoolpainter.app.domain.models.OpenSpoolPayload

sealed interface OpenSpoolDecodeResult {
    data class Success(val payload: OpenSpoolPayload) : OpenSpoolDecodeResult
    data class Malformed(val reason: String) : OpenSpoolDecodeResult
    data object NotOpenSpool : OpenSpoolDecodeResult
}
