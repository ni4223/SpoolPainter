package com.spoolpainter.app.ui.components.sheets

import androidx.compose.runtime.Composable
import com.spoolpainter.app.ui.screens.main.ActiveFlow

@Composable
fun BottomSheetHost(
    activeFlow: ActiveFlow,
    repairConfirmState: RepairConfirmUiState,
    pairAnotherState: PairAnotherTagUiState?,
    onRepairConfirm: () -> Unit,
    onRepairDismiss: () -> Unit,
    onPairAnotherAccept: () -> Unit,
    onPairAnotherDismiss: () -> Unit,
) {
    when (activeFlow) {
        is ActiveFlow.AwaitingRepairConfirmation ->
            if (repairConfirmState.visible) {
                RepairConfirmSheet(
                    state = repairConfirmState,
                    onConfirm = onRepairConfirm,
                    onDismiss = onRepairDismiss,
                )
            }
        is ActiveFlow.PromptingPairAnother ->
            pairAnotherState?.takeIf { it.visible }?.let {
                PairAnotherTagSheet(
                    state = it,
                    onAccept = onPairAnotherAccept,
                    onDismiss = onPairAnotherDismiss,
                )
            }
        else -> Unit
    }
}
