package com.spoolpainter.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spoolpainter.app.domain.models.SpoolmanFilament

/**
 * U8-Δ-1 — collapsed-by-default "Filament ▾" expander hosting a single
 * picker that lists ALL filaments (no orphan filter; reframe §1.4).
 * Default state collapsed; user toggles via the header row.
 */
@Composable
fun FilamentSectionExpander(
    filaments: List<SpoolmanFilament>,
    selectedFilamentId: Int?,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onSelect: (SpoolmanFilament?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("expander-filament"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(vertical = 8.dp)
                .testTag("expander-filament-header"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse filament picker" else "Expand filament picker",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Filament",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp)
                    .testTag("expander-filament-content"),
            ) {
                Text(
                    text = "Already have this filament in Spoolman? Pick it. Otherwise we'll create a new one from the form below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                FilamentPicker(
                    filaments = filaments,
                    selectedFilamentId = selectedFilamentId,
                    enabled = enabled,
                    onSelect = onSelect,
                )
            }
        }
    }
}
