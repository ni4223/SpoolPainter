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
        others: List<SpoolmanSpool>,
        targetSpoolId: Int,
        uid: CardUid,
    ): Boolean {
        // Supersede a still-pending request instead of throwing. A check()
        // here used to leak IllegalStateException into the calling NFC flow,
        // killing its coroutine and stranding the viewmodel's activeFlow in
        // flight (the "app wedged after one spool" bug). The stale awaiter
        // completes with false and cleanly takes the Declined path.
        val stale = pendingResult
        if (stale != null || _pendingRequest.value != null) {
            android.util.Log.w(
                "MoveOnBindConfirmer",
                "superseding a still-pending confirm request (uid=${uid.hex})",
            )
            stale?.complete(false)
            pendingResult = null
            _pendingRequest.value = null
        }
        val deferred = CompletableDeferred<Boolean>()
        val request = RepairConfirmRequest(others, targetSpoolId, uid)
        pendingResult = deferred
        _pendingRequest.value = request
        return try {
            deferred.await()
        } finally {
            // Identity-guarded teardown: a superseding confirm() may already
            // have installed its own deferred/request, and its resumption
            // (Unconfined dispatchers resume synchronously) must not clobber
            // the newer pair.
            if (pendingResult === deferred) pendingResult = null
            if (_pendingRequest.value === request) _pendingRequest.value = null
        }
    }

    override fun submitResult(confirm: Boolean) {
        pendingResult?.complete(confirm)
    }
}
