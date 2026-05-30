package com.spoolpainter.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spoolpainter.app.data.local.SortDirection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T : Enum<T>> SettingsSortSection(
    label: String,
    selectedKey: T,
    direction: SortDirection,
    keys: Array<T>,
    keyLabel: (T) -> String,
    onKeySelected: (T) -> Unit,
    onDirectionChanged: (SortDirection) -> Unit,
    testTag: String,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = keyLabel(selectedKey),
                onValueChange = {},
                readOnly = true,
                label = { Text("Sort by") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .testTag("$testTag-key"),
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                shape = RoundedCornerShape(20.dp),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                keys.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(keyLabel(option)) },
                        onClick = {
                            expanded = false
                            if (option != selectedKey) onKeySelected(option)
                        },
                        modifier = Modifier.testTag("$testTag-key-${option.name.lowercase()}"),
                    )
                }
            }
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = direction == SortDirection.Asc,
                onClick = {
                    if (direction != SortDirection.Asc) onDirectionChanged(SortDirection.Asc)
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {},
                modifier = Modifier.testTag("$testTag-direction-asc"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Ascending")
                }
            }
            SegmentedButton(
                selected = direction == SortDirection.Desc,
                onClick = {
                    if (direction != SortDirection.Desc) onDirectionChanged(SortDirection.Desc)
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {},
                modifier = Modifier.testTag("$testTag-direction-desc"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Descending")
                }
            }
        }
    }
}
