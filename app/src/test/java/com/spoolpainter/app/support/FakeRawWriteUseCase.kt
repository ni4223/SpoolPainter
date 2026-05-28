package com.spoolpainter.app.support

import com.spoolpainter.app.domain.usecases.RawWriteInput
import com.spoolpainter.app.domain.usecases.RawWriteResult
import com.spoolpainter.app.domain.usecases.RawWriteUseCase
import com.spoolpainter.app.hardware.nfc.NfcRepository

class FakeRawWriteUseCase(nfc: NfcRepository) : RawWriteUseCase(nfc) {
    var nextResult: RawWriteResult = RawWriteResult.Cancelled("not configured")
    var invokeCalls: Int = 0
        private set
    var lastInput: RawWriteInput? = null
        private set

    override suspend fun invoke(input: RawWriteInput): RawWriteResult {
        invokeCalls++
        lastInput = input
        return nextResult
    }
}
