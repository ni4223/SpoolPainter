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
fun PairAnotherTagSheet(
    state: PairAnotherTagUiState,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("pair-another-tag-sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Pair another tag with this spool?",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "We'll write the same data to the second tag and remember both.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("pair-another-tag-sheet-done"),
                ) {
                    Text("Done")
                }
                FilledTonalButton(
                    onClick = onAccept,
                    modifier = Modifier.testTag("pair-another-tag-sheet-accept"),
                ) {
                    Text("Pair another")
                }
            }
        }
    }
}
