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
    enabled: Boolean,
    sortKey: FilamentSortKey,
    sortDirection: SortDirection,
    onSelect: (SpoolmanFilament?) -> Unit,
    modifier: Modifier = Modifier,
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
        Text(
            text = "Already have this filament in Spoolman? Pick it. Otherwise we'll create a new one from the form below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilamentPicker(
            filaments = filaments,
            selectedFilamentId = selectedFilamentId,
            enabled = enabled,
            sortKey = sortKey,
            sortDirection = sortDirection,
            onSelect = onSelect,
            prominent = true,
            modifier = Modifier.testTag("expander-filament-content"),
        )
    }
}
