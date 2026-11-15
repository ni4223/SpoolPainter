package com.spoolpainter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.domain.models.ExternalFilament
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.ui.screens.main.ExternalSearchUiState

@Composable
fun FilamentSection(
    filaments: List<SpoolmanFilament>,
    selectedFilamentId: Int?,
    selectedSpoolId: Int?,
    enabled: Boolean,
    sortKey: FilamentSortKey,
    sortDirection: SortDirection,
    onSelect: (SpoolmanFilament?) -> Unit,
    modifier: Modifier = Modifier,
    scanSuggestedFilamentIds: List<Int> = emptyList(),
    // SpoolmanDB — origin chip + the picker's external search section.
    externalFilamentId: String? = null,
    externalDbLabel: String = "SpoolmanDB",
    externalSearchState: ExternalSearchUiState = ExternalSearchUiState.Idle,
    onExternalFilamentQueryChanged: (String) -> Unit = {},
    onExternalFilamentLoadMore: () -> Unit = {},
    onExternalFilamentPicked: (ExternalFilament) -> Unit = {},
    onExternalSelectionCleared: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("expander-filament"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Filament",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        // State-aware hint. Spool selected: no hint (form is locked, the
        // picker is for another flow). Filament selected: tell the user
        // Save creates a fresh spool of that filament. Nothing selected:
        // encourage pick or fill.
        val hint = when {
            selectedSpoolId != null -> null
            selectedFilamentId != null ->
                "Tap Save to create a spool for this filament."
            else ->
                "Select a filament, or fill in the details to create one."
        }
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // SpoolmanDB origin chip: shown while the form was seeded from an
        // external database pick. Dismissing keeps the prefilled values,
        // it only drops the external linkage.
        if (externalFilamentId != null) {
            InputChip(
                selected = false,
                onClick = onExternalSelectionCleared,
                label = { Text("From $externalDbLabel") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear database origin",
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier.testTag("filament-origin-chip"),
            )
        }
        FilamentPicker(
            filaments = filaments,
            selectedFilamentId = selectedFilamentId,
            enabled = enabled,
            sortKey = sortKey,
            sortDirection = sortDirection,
            onSelect = onSelect,
            prominent = true,
            modifier = Modifier.testTag("expander-filament-content"),
            scanSuggestedFilamentIds = scanSuggestedFilamentIds,
            externalDbLabel = externalDbLabel,
            externalSearchState = externalSearchState,
            onExternalFilamentQueryChanged = onExternalFilamentQueryChanged,
            onExternalFilamentLoadMore = onExternalFilamentLoadMore,
            onExternalFilamentPicked = onExternalFilamentPicked,
        )
    }
}
