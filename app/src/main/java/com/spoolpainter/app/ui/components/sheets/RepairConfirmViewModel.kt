package com.spoolpainter.app.ui.components.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.usecases.MoveOnBindConfirmer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * UI state for [RepairConfirmSheet]. [otherSpoolDisplays] lists 1..N current
 * owners of the tapped UID — usually 1, but ≥2 when the UID was bound to
 * multiple spools (data conflict). The user's "Move it" sweeps all of them.
 */
data class RepairConfirmUiState(
    val otherSpoolDisplays: List<String>,
    val targetSpoolId: Int,
    val uid: CardUid,
    val visible: Boolean,
)

@HiltViewModel
class RepairConfirmViewModel @Inject constructor(
    private val confirmer: MoveOnBindConfirmer,
) : ViewModel() {

    val uiState: StateFlow<RepairConfirmUiState> =
        confirmer.pendingRequest
            .map { req ->
                if (req == null) {
                    HIDDEN
                } else {
                    RepairConfirmUiState(
                        otherSpoolDisplays = req.others.map(::displayName),
                        targetSpoolId = req.targetSpoolId,
                        uid = req.uid,
                        visible = true,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), HIDDEN)

    fun onConfirm() {
        confirmer.submitResult(true)
    }

    fun onDismiss() {
        confirmer.submitResult(false)
    }

    private fun displayName(spool: SpoolmanSpool): String {
        val filament = spool.filament
        val parts = listOfNotNull(
            filament.vendor?.name?.takeIf { it.isNotBlank() },
            filament.material?.takeIf { it.isNotBlank() },
            filament.color_hex?.takeIf { it.isNotBlank() },
        )
        val prefix = parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
        return if (prefix != null) "$prefix #${spool.id ?: "?"}" else "spool #${spool.id ?: "?"}"
    }

    private companion object {
        val HIDDEN = RepairConfirmUiState(
            otherSpoolDisplays = emptyList(),
            targetSpoolId = 0,
            uid = CardUid(""),
            visible = false,
        )
    }
}
