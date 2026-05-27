package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid

interface MoveOnBindUseCase {
    suspend operator fun invoke(uid: CardUid, targetSpoolId: Int): Outcome

    sealed interface Outcome {
        data object Proceed : Outcome
        /** UID was moved off [fromSpoolIds] (one or more) onto the target. */
        data class Moved(val fromSpoolIds: List<Int>) : Outcome
        data object Declined : Outcome
        data class Failed(val reason: String, val partiallyModifiedSpoolIds: List<Int>) : Outcome
    }
}
