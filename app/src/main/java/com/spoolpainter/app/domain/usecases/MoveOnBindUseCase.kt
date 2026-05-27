package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid

interface MoveOnBindUseCase {
    suspend operator fun invoke(uid: CardUid, targetSpoolId: Int): Outcome

    sealed interface Outcome {
        data object Proceed : Outcome
        data class Moved(val fromSpoolId: Int) : Outcome
        data object Declined : Outcome
        data class Failed(val reason: String, val partiallyModifiedSpoolId: Int?) : Outcome
        data class AmbiguousOwnership(val currentOwners: List<SpoolmanSpool>) : Outcome
    }
}
