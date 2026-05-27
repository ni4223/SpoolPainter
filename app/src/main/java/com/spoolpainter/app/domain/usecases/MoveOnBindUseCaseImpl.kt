package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid
import javax.inject.Inject

class MoveOnBindUseCaseImpl @Inject constructor(
    private val spoolman: SpoolmanRepository,
    private val confirmer: MoveOnBindConfirmer,
) : MoveOnBindUseCase {

    override suspend fun invoke(uid: CardUid, targetSpoolId: Int): MoveOnBindUseCase.Outcome {
        val matches = when (val outcome = spoolman.findSpoolsByCardUid(uid)) {
            is SpoolmanOutcome.Success -> outcome.data
            else -> return MoveOnBindUseCase.Outcome.Failed(
                reason = humanReadable(outcome),
                partiallyModifiedSpoolId = null,
            )
        }
        return when {
            matches.isEmpty() -> MoveOnBindUseCase.Outcome.Proceed
            matches.size == 1 && matches.single().id == targetSpoolId ->
                MoveOnBindUseCase.Outcome.Proceed
            matches.size == 1 -> performMove(uid, targetSpoolId, matches.single())
            else -> MoveOnBindUseCase.Outcome.AmbiguousOwnership(matches)
        }
    }

    private suspend fun performMove(
        uid: CardUid,
        targetSpoolId: Int,
        other: SpoolmanSpool,
    ): MoveOnBindUseCase.Outcome {
        val confirmed = confirmer.confirm(other, targetSpoolId, uid)
        if (!confirmed) return MoveOnBindUseCase.Outcome.Declined

        val otherId = other.id
            ?: return MoveOnBindUseCase.Outcome.Failed(
                reason = "owning spool has no id",
                partiallyModifiedSpoolId = null,
            )

        when (val rmv = spoolman.removeCardUidFromSpool(otherId, uid)) {
            is SpoolmanOutcome.Success -> Unit
            else -> return MoveOnBindUseCase.Outcome.Failed(
                reason = humanReadable(rmv),
                partiallyModifiedSpoolId = null,
            )
        }
        when (val apd = spoolman.appendCardUidToSpool(targetSpoolId, uid)) {
            is SpoolmanOutcome.Success -> Unit
            else -> return MoveOnBindUseCase.Outcome.Failed(
                reason = humanReadable(apd),
                partiallyModifiedSpoolId = otherId,
            )
        }
        return MoveOnBindUseCase.Outcome.Moved(fromSpoolId = otherId)
    }

    private fun humanReadable(outcome: SpoolmanOutcome<*>): String = when (outcome) {
        is SpoolmanOutcome.HttpError -> "HTTP ${outcome.code}: ${outcome.message}"
        is SpoolmanOutcome.NetworkError ->
            "Network: ${outcome.cause.message ?: outcome.cause::class.simpleName}"
        is SpoolmanOutcome.ParseError ->
            "Parse: ${outcome.cause.message ?: outcome.cause::class.simpleName}"
        is SpoolmanOutcome.Success<*> -> "Success"
    }
}
