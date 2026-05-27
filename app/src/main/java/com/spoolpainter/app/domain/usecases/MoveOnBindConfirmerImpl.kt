package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoveOnBindConfirmerImpl @Inject constructor() : MoveOnBindConfirmer {

    private val _pendingRequest = MutableStateFlow<RepairConfirmRequest?>(null)
    private var pendingResult: CompletableDeferred<Boolean>? = null

    override val pendingRequest: StateFlow<RepairConfirmRequest?> = _pendingRequest.asStateFlow()

    override suspend fun confirm(
        other: SpoolmanSpool,
        targetSpoolId: Int,
        uid: CardUid,
    ): Boolean {
        check(_pendingRequest.value == null && pendingResult == null) {
            "MoveOnBindConfirmer: another request is already pending"
        }
        val deferred = CompletableDeferred<Boolean>()
        pendingResult = deferred
        _pendingRequest.value = RepairConfirmRequest(other, targetSpoolId, uid)
        return try {
            deferred.await()
        } finally {
            pendingResult = null
            _pendingRequest.value = null
        }
    }

    override fun submitResult(confirm: Boolean) {
        pendingResult?.complete(confirm)
    }
}
