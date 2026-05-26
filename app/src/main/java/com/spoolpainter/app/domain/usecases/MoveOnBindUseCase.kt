package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.domain.primitives.CardUid
import javax.inject.Inject

interface MoveOnBindUseCase {
    suspend operator fun invoke(uid: CardUid, targetSpoolId: Int): Outcome

    sealed interface Outcome {
        data object Proceed : Outcome
        // U6b adds: RequireConfirmation, ConfirmedAndMoved, Declined.
    }

    class NoOp @Inject constructor() : MoveOnBindUseCase {
        override suspend fun invoke(uid: CardUid, targetSpoolId: Int): Outcome = Outcome.Proceed
    }
}
