package com.spoolpainter.app.support

import com.spoolpainter.app.domain.usecases.MoveOnBindUseCase
import com.spoolpainter.app.domain.usecases.TwoTagInput
import com.spoolpainter.app.domain.usecases.TwoTagResult
import com.spoolpainter.app.domain.usecases.TwoTagUseCase

class FakeTwoTagUseCase(
    nfc: FakeNfcRepository = FakeNfcRepository(),
    spoolman: FakeSpoolmanRepository = FakeSpoolmanRepository(),
    moveOnBind: MoveOnBindUseCase = MoveOnBindNoOp,
) : TwoTagUseCase(nfc, spoolman, moveOnBind) {

    var nextResult: TwoTagResult = TwoTagResult.Cancelled("not-staged")

    var invokeCalls: Int = 0
        private set
    var lastInput: TwoTagInput? = null
        private set
    var nextDelayMs: Long = 0L

    override suspend fun invoke(input: TwoTagInput): TwoTagResult {
        invokeCalls++
        lastInput = input
        if (nextDelayMs > 0L) {
            kotlinx.coroutines.delay(nextDelayMs)
        }
        return nextResult
    }
}
