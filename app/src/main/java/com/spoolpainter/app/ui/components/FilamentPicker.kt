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
) {
    var expanded by remember { mutableStateOf(false) }
    val sorted = remember(filaments, sortKey, sortDirection) {
        filaments.sortedWith(filamentComparator(sortKey, sortDirection))
    }
    val selected = sorted.firstOrNull { it.id == selectedFilamentId }
    val displayValue = selected?.displayString().orEmpty()

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
            placeholder = { Text("Optional") },
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
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.clip(RoundedCornerShape(20.dp)),
            ) {
                sorted.forEach { filament ->
                    DropdownMenuItem(
                        text = { Text(filament.displayString()) },
                        onClick = {
                            expanded = false
                            onSelect(filament)
                        },
                        modifier = Modifier.testTag("filament-picker-row-${filament.id}"),
                    )
                }
            }
        }
    }
}

private fun SpoolmanFilament.displayString(): String {
    val vendorName = vendor?.name?.takeIf { it.isNotBlank() } ?: "Unknown"
    val filamentName = name?.takeIf { it.isNotBlank() } ?: material ?: "Unknown"
    val variantValue = extra?.get("variant")?.let { decodeJsonString(it) }
    return if (variantValue.isNullOrBlank()) {
        "$vendorName · $filamentName"
    } else {
        "$vendorName · $filamentName · $variantValue"
    }
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
