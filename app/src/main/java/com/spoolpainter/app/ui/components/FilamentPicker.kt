package com.spoolpainter.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.domain.models.SpoolmanFilament

/**
 * Rendered inside the always-open Filament section. Lists ALL filaments
 * alphabetically by vendor + name + variant. Tapping a row prefills the form
 * via MainViewModel.onFilamentSelected; the X clears the selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilamentPicker(
    filaments: List<SpoolmanFilament>,
    selectedFilamentId: Int?,
    enabled: Boolean,
    sortKey: FilamentSortKey,
    sortDirection: SortDirection,
    onSelect: (SpoolmanFilament?) -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    // U20 (UI-49, F2) — scan-suggested filament ids float to the top when the
    // picker is opened, in scorer-rank order (best first), separated by a thin
    // divider (no header). Passive: never changes the selection.
    scanSuggestedFilamentIds: List<Int> = emptyList(),
) {
    var expanded by remember { mutableStateOf(false) }
    val anchor = rememberLazyDropdownAnchor()
    val sorted = remember(filaments, sortKey, sortDirection) {
        filaments.sortedWith(filamentComparator(sortKey, sortDirection))
    }
    val filamentRank = remember(scanSuggestedFilamentIds) {
        scanSuggestedFilamentIds.withIndex().associate { (i, id) -> id to i }
    }
    val ranked = remember(sorted, filamentRank) {
        com.spoolpainter.app.domain.primitives.PickerRanking.partitionRanked(sorted) {
            filamentRank[it.id]
        }
    }
    val orderedFilaments = ranked.rows
    val suggestedCount = ranked.suggestedCount
    // Cache the per-row display tuple so a recompose doesn't re-run the
    // string formatters on every entry. ExposedDropdownMenu renders all
    // items eagerly, so this is per-row work multiplied by N filaments.
    val rows = remember(orderedFilaments) {
        orderedFilaments.map { filament ->
            FilamentRowDisplay(
                filament = filament,
                primary = filament.primaryRowText(),
                secondary = filament.secondaryRowText(),
                colorHex = filament.color_hex,
            )
        }
    }
    val dividerRowKey = remember(rows, suggestedCount) {
        if (suggestedCount in 1 until rows.size) rows[suggestedCount].filament.id else null
    }
    val selected = sorted.firstOrNull { it.id == selectedFilamentId }
    val displayValue = selected?.selectedDisplay().orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
            .fillMaxWidth()
            .testTag("filament-picker"),
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Pick existing filament") },
            placeholder = { Text("Filaments in Spoolman") },
            trailingIcon = {
                if (selected != null && enabled) {
                    IconButton(
                        onClick = {
                            // Force the dropdown closed: ExposedDropdownMenuBox
                            // may toggle on trailing-icon taps and we don't
                            // want a clear to also pop the menu open.
                            expanded = false
                            onSelect(null)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("filament-picker-clear"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear filament selection",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .then(anchor.modifier)
                .testTag("filament-picker-input"),
            textStyle = if (prominent) {
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            shape = RoundedCornerShape(20.dp),
        )
        if (enabled && sorted.isNotEmpty()) {
            // LazyDropdownMenu (custom) instead of ExposedDropdownMenu —
            // the latter composes every row eagerly, which lags noticeably
            // at 50+ filaments. Lazy compose drops first-open work to
            // O(visible rows).
            LazyDropdownMenu(
                expanded = expanded,
                items = rows,
                anchor = anchor,
                onDismiss = { expanded = false },
                onItemClick = { row ->
                    expanded = false
                    onSelect(row.filament)
                },
                itemKey = { row -> row.filament.id },
                itemContent = { row ->
                    PickerRow(
                        primary = row.primary,
                        secondary = row.secondary,
                        colorHex = row.colorHex,
                        modifier = Modifier.testTag("filament-picker-row-${row.filament.id}"),
                    )
                },
                dividerBefore = dividerRowKey?.let { key -> { row -> row.filament.id == key } },
            )
        }
    }
}

@androidx.compose.runtime.Immutable
private data class FilamentRowDisplay(
    val filament: SpoolmanFilament,
    val primary: String,
    val secondary: String,
    val colorHex: String?,
)

/**
 * Compact text for the picker's text field after selection. User direction:
 * "on selection just show name and id" — brand + colour + material flow into
 * the form fields below, so re-stating them in the picker is noise.
 */
private fun SpoolmanFilament.selectedDisplay(): String {
    val filamentName = name?.takeIf { it.isNotBlank() } ?: material ?: "Unknown"
    return "$filamentName · #$id"
}

/** Bold first line of the open-dropdown row. */
internal fun SpoolmanFilament.primaryRowText(): String {
    val vendorName = vendor?.name?.takeIf { it.isNotBlank() }
    val filamentName = name?.takeIf { it.isNotBlank() } ?: material ?: "Unknown"
    return if (vendorName != null) "$vendorName · $filamentName" else filamentName
}

/** Faded second line: 'Material · Variant · #id' (variant only when set). */
internal fun SpoolmanFilament.secondaryRowText(): String {
    val parts = listOfNotNull(
        material?.takeIf { it.isNotBlank() },
        extra?.get("variant")?.let { decodeJsonString(it) }?.takeIf { it.isNotBlank() },
        "#$id",
    )
    return parts.joinToString(" · ")
}

private fun decodeJsonString(raw: String?): String? {
    if (raw.isNullOrEmpty()) return null
    val unwrapped = if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
        raw.substring(1, raw.length - 1)
    } else {
        raw
    }
    return unwrapped.takeIf { it.isNotBlank() }
}
