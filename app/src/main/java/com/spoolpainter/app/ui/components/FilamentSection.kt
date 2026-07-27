package com.spoolpainter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.domain.models.SpoolmanFilament

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
        )
    }
}
