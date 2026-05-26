package com.spoolpainter.app.support

import com.spoolpainter.app.domain.usecases.CreateAndPairInput
import com.spoolpainter.app.domain.usecases.CreateAndPairResult
import com.spoolpainter.app.domain.usecases.CreateAndPairUseCase
import com.spoolpainter.app.domain.usecases.MoveOnBindUseCase

class FakeCreateAndPairUseCase(
    nfc: FakeNfcRepository = FakeNfcRepository(),
    spoolman: FakeSpoolmanRepository = FakeSpoolmanRepository(),
    moveOnBind: MoveOnBindUseCase = MoveOnBindUseCase.NoOp(),
) : CreateAndPairUseCase(nfc, spoolman, moveOnBind) {

    var nextResult: CreateAndPairResult = CreateAndPairResult.Cancelled("not-staged")

    var invokeCalls: Int = 0
        private set
    var lastInput: CreateAndPairInput? = null
        private set

    var nextDelayMs: Long = 0L

    override suspend fun invoke(snapshot: CreateAndPairInput): CreateAndPairResult {
        invokeCalls++
        lastInput = snapshot
        if (nextDelayMs > 0L) {
            kotlinx.coroutines.delay(nextDelayMs)
        }
        return nextResult
    }
}
