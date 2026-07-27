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
    // U21 (UI-48) — type-to-search query. Reset whenever the picker closes so
    // the next open starts fresh (D5); a non-blank query drops the U20 float.
    var query by remember { mutableStateOf("") }
    val anchor = rememberLazyDropdownAnchor()
    val sorted = remember(filaments, sortKey, sortDirection) {
        filaments.sortedWith(filamentComparator(sortKey, sortDirection))
    }
    // Cache the per-row display tuple (built once per sorted change) so a
    // recompose / keystroke doesn't re-run the string formatters per entry.
    val sortedRows = remember(sorted) {
        sorted.map { filament ->
            FilamentRowDisplay(
                filament = filament,
                primary = filament.primaryRowText(),
                secondary = filament.secondaryRowText(),
                colorHex = filament.color_hex,
                searchText = "${filament.primaryRowText()} ${filament.secondaryRowText()}",
            )
        }
    }
    val filamentRank = remember(scanSuggestedFilamentIds) {
        scanSuggestedFilamentIds.withIndex().associate { (i, id) -> id to i }
    }
    // With an active query, filter to matches in normal sort — the U20 float is
    // suppressed (Q-U21-1). With a blank query, float scan-suggested rows to the
    // top with a divider exactly as before.
    val rows: List<FilamentRowDisplay>
    val dividerRowKey: Int?
    if (query.isNotBlank()) {
        rows = com.spoolpainter.app.domain.primitives.PickerRanking.filter(
            sortedRows, query,
        ) { it.searchText }
        dividerRowKey = null
    } else {
        val ranked = com.spoolpainter.app.domain.primitives.PickerRanking.partitionRanked(
            sortedRows,
        ) { filamentRank[it.filament.id] }
        rows = ranked.rows
        dividerRowKey = if (ranked.suggestedCount in 1 until rows.size) {
            rows[ranked.suggestedCount].filament.id
        } else {
            null
        }
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
                            query = ""
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
                onDismiss = {
                    expanded = false
                    query = ""
                },
                onItemClick = { row ->
                    expanded = false
                    query = ""
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
                header = {
                    PickerSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Search filaments",
                        testTag = "filament-picker-search",
                    )
                },
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
    // U21 — precomputed lowercase-search target: primary + secondary text
    // (which already folds in vendor, name, material, variant, and #id).
    val searchText: String,
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
