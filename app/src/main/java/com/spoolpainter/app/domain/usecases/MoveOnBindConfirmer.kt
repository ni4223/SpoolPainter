package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid
import kotlinx.coroutines.flow.StateFlow

interface MoveOnBindConfirmer {
    suspend fun confirm(other: SpoolmanSpool, targetSpoolId: Int, uid: CardUid): Boolean
    val pendingRequest: StateFlow<RepairConfirmRequest?>
    fun submitResult(confirm: Boolean)
}

data class RepairConfirmRequest(
    val other: SpoolmanSpool,
    val targetSpoolId: Int,
    val uid: CardUid,
)
