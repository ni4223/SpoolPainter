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
                partiallyModifiedSpoolIds = emptyList(),
            )
        }
        // Sources to sweep are everyone except the target spool. Self-match
        // (size==1 && id==target) is just Proceed.
        val sources = matches.filter { it.id != targetSpoolId }
        return when {
            sources.isEmpty() -> MoveOnBindUseCase.Outcome.Proceed
            else -> performMove(uid, targetSpoolId, sources)
        }
    }

    private suspend fun performMove(
        uid: CardUid,
        targetSpoolId: Int,
        sources: List<SpoolmanSpool>,
    ): MoveOnBindUseCase.Outcome {
        val confirmed = confirmer.confirm(sources, targetSpoolId, uid)
        if (!confirmed) return MoveOnBindUseCase.Outcome.Declined

        val moved = mutableListOf<Int>()
        for (source in sources) {
            val sourceId = source.id
                ?: return MoveOnBindUseCase.Outcome.Failed(
                    reason = "owning spool has no id",
                    partiallyModifiedSpoolIds = moved.toList(),
                )
            when (val rmv = spoolman.removeCardUidFromSpool(sourceId, uid)) {
                is SpoolmanOutcome.Success -> moved += sourceId
                else -> return MoveOnBindUseCase.Outcome.Failed(
                    reason = humanReadable(rmv),
                    partiallyModifiedSpoolIds = moved.toList(),
                )
            }
        }
        // Sentinel target id (negative) means the caller will append to the
        // real new spool itself once it's been created. Used by
        // VendorUidOnlyPairUseCase's new-spool path where the target doesn't
        // exist yet at precheck time.
        if (targetSpoolId >= 0) {
            when (val apd = spoolman.appendCardUidToSpool(targetSpoolId, uid)) {
                is SpoolmanOutcome.Success -> Unit
                else -> return MoveOnBindUseCase.Outcome.Failed(
                    reason = humanReadable(apd),
                    partiallyModifiedSpoolIds = moved.toList(),
                )
            }
        }
        return MoveOnBindUseCase.Outcome.Moved(fromSpoolIds = moved.toList())
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
