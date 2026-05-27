package com.spoolpainter.app.support

import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.usecases.MoveOnBindUseCase

class FakeMoveOnBindUseCase : MoveOnBindUseCase {
    var nextOutcome: MoveOnBindUseCase.Outcome = MoveOnBindUseCase.Outcome.Proceed

    var invokeCalls: Int = 0
        private set
    var lastUid: CardUid? = null
        private set
    var lastTargetSpoolId: Int? = null
        private set

    override suspend fun invoke(uid: CardUid, targetSpoolId: Int): MoveOnBindUseCase.Outcome {
        invokeCalls++
        lastUid = uid
        lastTargetSpoolId = targetSpoolId
        return nextOutcome
    }
}
