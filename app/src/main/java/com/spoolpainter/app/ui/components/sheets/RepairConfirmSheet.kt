package com.spoolpainter.app.ui.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairConfirmSheet(
    state: RepairConfirmUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("repair-confirm-sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Re-pair this tag to the selected spool?",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Currently on: ${state.otherSpoolDisplay}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("repair-confirm-sheet-cancel"),
                ) {
                    Text("Cancel")
                }
                FilledTonalButton(
                    onClick = onConfirm,
                    modifier = Modifier.testTag("repair-confirm-sheet-confirm"),
                ) {
                    Text("Move it")
                }
            }
        }
    }
}
