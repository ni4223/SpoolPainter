package com.spoolpainter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.domain.models.ExternalFilament
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.ui.screens.main.ExternalSearchUiState

/**
 * Rendered inside the always-open Filament section. Lists ALL filaments
 * alphabetically by vendor + name + variant. Tapping a row prefills the form
 * via MainViewModel.onFilamentSelected; the X clears the selection.
 * Below the local rows sits the SpoolmanDB section (server ≥ 0.26): the
 * same search field drives the local filter AND a debounced external
 * database search; picking a DB row prefills the form for a fresh import.
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
    // U20 (UI-49, F2) — these ids float to the top when the picker is opened, in
    // scorer-rank order (best first), separated by a thin divider (no header).
    // Passive: never changes the selection. U24 (UI-59) added a second source
    // for the same list (the form's own fields, when there is no scan set), so
    // this parameter is source-agnostic on purpose — the ViewModel resolves
    // which one wins.
    suggestedFilamentIds: List<Int> = emptyList(),
    // SpoolmanDB section. The picker forwards the live query to the ViewModel
    // (debounced server search) while the local filter keeps using it as-is.
    externalDbLabel: String = "SpoolmanDB",
    externalSearchState: ExternalSearchUiState = ExternalSearchUiState.Idle,
    onExternalFilamentQueryChanged: (String) -> Unit = {},
    onExternalFilamentLoadMore: () -> Unit = {},
    onExternalFilamentPicked: (ExternalFilament) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    // U21 (UI-48) — type-to-search query. Reset whenever the picker closes so
    // the next open starts fresh (D5); a non-blank query drops the U20 float.
    var query by remember { mutableStateOf("") }
    val anchor = rememberLazyDropdownAnchor()
    val sorted = remember(filaments, sortKey, sortDirection) {
        filaments.sortedWith(filamentComparator(sortKey, sortDirection))
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
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
    val filamentRank = remember(suggestedFilamentIds) {
        suggestedFilamentIds.withIndex().associate { (i, id) -> id to i }
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
    // SpoolmanDB: hidden entirely while Idle with a blank query; any other
    // state (or a started query) renders the in-list section.
    val showExternalSection = externalSearchState !is ExternalSearchUiState.Idle || query.isNotBlank()

    fun closeAndResetQuery() {
        query = ""
        onExternalFilamentQueryChanged("")
    }

    // Focus the search field the moment the dropdown opens so typing can
    // start immediately. The popup content may not be attached to the focus
    // hierarchy on the first frame — a failed request is retried once after
    // a frame instead of crashing. Closing hides the keyboard so it never
    // lingers over the form.
    LaunchedEffect(expanded) {
        if (expanded) {
            runCatching { focusRequester.requestFocus() }
                .onFailure { withFrameNanos { }; runCatching { focusRequester.requestFocus() } }
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

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
                            closeAndResetQuery()
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
                    closeAndResetQuery()
                },
                onItemClick = { row ->
                    expanded = false
                    closeAndResetQuery()
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
                        onQueryChange = { newQuery ->
                            query = newQuery
                            onExternalFilamentQueryChanged(newQuery)
                        },
                        placeholder = "Search filaments",
                        testTag = "filament-picker-search",
                        focusRequester = focusRequester,
                    )
                },
                footerItems = if (showExternalSection) {
                    {
                        externalDbSectionItems(
                            label = externalDbLabel,
                            state = externalSearchState,
                            onPick = { item ->
                                expanded = false
                                closeAndResetQuery()
                                onExternalFilamentPicked(item)
                            },
                            onLoadMore = onExternalFilamentLoadMore,
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * SpoolmanDB section appended INSIDE the scrolling dropdown list, below the
 * local filament rows — so a long result set scrolls as one list instead of
 * clipping. State-driven: gate row (< 0.26), keep-typing hint, searching,
 * result rows + load-more, or a muted error row. Picking closes the dropdown
 * like a local pick.
 *
 * Item keys: structural rows use the shared "external-section-*" prefix
 * (divider/header/state/error/load-more); result rows use "external-${id}"
 * so paging in more rows reuses composed items.
 */
private fun LazyListScope.externalDbSectionItems(
    label: String,
    state: ExternalSearchUiState,
    onPick: (ExternalFilament) -> Unit,
    onLoadMore: () -> Unit,
) {
    item(key = "external-section-divider") {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    item(key = "external-section-header") {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("filament-picker-external-section")
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
        )
    }
    when (state) {
        ExternalSearchUiState.Unsupported ->
            mutedStateItem("Database search requires Spoolman v0.26+")
        ExternalSearchUiState.Idle ->
            mutedStateItem("Keep typing to search the database")
        ExternalSearchUiState.Searching ->
            mutedStateItem("Searching…")
        is ExternalSearchUiState.Error ->
            item(key = "external-section-error") {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        is ExternalSearchUiState.Results -> {
            if (state.items.isEmpty()) {
                mutedStateItem("No matches in $label")
            } else {
                items(items = state.items, key = { "external-${it.id}" }) { item ->
                    ExternalFilamentRow(item = item, onClick = { onPick(item) })
                }
            }
            val remaining = state.totalCount - state.items.size
            if (remaining > 0) {
                item(key = "external-section-load-more") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onLoadMore)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("filament-picker-external-load-more"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Load more ($remaining more)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** One muted status row (gate / keep-typing / searching / no-matches). All
 *  share the single "external-section-state" key — exactly one ever renders. */
private fun LazyListScope.mutedStateItem(text: String) {
    item(key = "external-section-state") {
        ExternalMutedRow(text)
    }
}

@Composable
private fun ExternalMutedRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun ExternalFilamentRow(item: ExternalFilament, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("filament-picker-external-row-${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExternalSwatch(
            colorHex = item.colorHex,
            colorHexes = item.colorHexes,
        )
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${item.manufacturer} · ${item.name}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.material,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 24dp swatch; multi-colour entries (colorHexes) layer the second colour
 *  bottom-end with a surface ring so multi-colour filaments read at a glance. */
@Composable
private fun ExternalSwatch(colorHex: String?, colorHexes: List<String>?) {
    val primary = colorHex ?: colorHexes?.firstOrNull()
    val second = colorHexes?.getOrNull(1)
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    val ring = MaterialTheme.colorScheme.surfaceContainer
    Box(modifier = Modifier.size(24.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(parseExternalSwatchColor(primary) ?: fallback),
        )
        if (second != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .border(1.5.dp, ring, CircleShape)
                    .clip(CircleShape)
                    .background(parseExternalSwatchColor(second) ?: fallback),
            )
        }
    }
}

private fun parseExternalSwatchColor(hex: String?): Color? {
    val cleaned = hex?.removePrefix("#") ?: return null
    if (cleaned.length != 6) return null
    return try {
        Color(
            red = cleaned.substring(0, 2).toInt(16),
            green = cleaned.substring(2, 4).toInt(16),
            blue = cleaned.substring(4, 6).toInt(16),
        )
    } catch (_: NumberFormatException) {
        null
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
