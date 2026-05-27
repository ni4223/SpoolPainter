package com.spoolpainter.app.support

import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.usecases.MoveOnBindConfirmer
import com.spoolpainter.app.domain.usecases.RepairConfirmRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

open class FakeMoveOnBindConfirmer : MoveOnBindConfirmer {

    private val _pendingRequest = MutableStateFlow<RepairConfirmRequest?>(null)
    override val pendingRequest: StateFlow<RepairConfirmRequest?> = _pendingRequest

    /** Tests set this to drive the next confirm() return value. */
    var nextResult: Boolean = true

    var lastRequest: RepairConfirmRequest? = null
        private set
    var confirmCalls: Int = 0
        private set
    var submitCalls: Int = 0
        private set
    var lastSubmitValue: Boolean? = null
        private set

    override suspend fun confirm(
        other: SpoolmanSpool,
        targetSpoolId: Int,
        uid: CardUid,
    ): Boolean {
        confirmCalls++
        val req = RepairConfirmRequest(other, targetSpoolId, uid)
        lastRequest = req
        _pendingRequest.value = req
        // Synchronous: return immediately. Production impl awaits a deferred,
        // but tests prefer the resolved-by-UI path collapsed into one step.
        return nextResult.also { _pendingRequest.value = null }
    }

    override fun submitResult(confirm: Boolean) {
        submitCalls++
        lastSubmitValue = confirm
    }

    /** Test helper: directly emit a pending request without going through confirm(). */
    fun emitPending(req: RepairConfirmRequest?) {
        _pendingRequest.value = req
    }
}
