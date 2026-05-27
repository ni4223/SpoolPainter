package com.spoolpainter.app.support

import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.usecases.MoveOnBindUseCase

/** Test-only no-op MoveOnBindUseCase — always returns Proceed. */
object MoveOnBindNoOp : MoveOnBindUseCase {
    override suspend fun invoke(uid: CardUid, targetSpoolId: Int): MoveOnBindUseCase.Outcome =
        MoveOnBindUseCase.Outcome.Proceed
}
