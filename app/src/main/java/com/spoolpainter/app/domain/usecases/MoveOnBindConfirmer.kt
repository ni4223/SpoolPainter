package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid
import kotlinx.coroutines.flow.StateFlow

interface MoveOnBindConfirmer {
    suspend fun confirm(others: List<SpoolmanSpool>, targetSpoolId: Int, uid: CardUid): Boolean
    val pendingRequest: StateFlow<RepairConfirmRequest?>
    fun submitResult(confirm: Boolean)
}

data class RepairConfirmRequest(
    /** All spools currently owning the UID — usually 1, can be ≥2 on data conflict. */
    val others: List<SpoolmanSpool>,
    val targetSpoolId: Int,
    val uid: CardUid,
)
