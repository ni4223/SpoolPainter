package com.spoolpainter.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Collapsable advanced section. Default collapsed so casual users never see
 * the optional key field. SpoolPainter neither bundles the value nor tells
 * the user where to look; the section only acknowledges that one can be
 * supplied if the user already has it.
 */
@Composable
internal fun SettingsVendorSection(
    bambuSalt: String,
    onBambuSaltSaved: (String) -> Unit,
    testTag: String,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(bambuSalt) { mutableStateOf(bambuSalt) }
    val saved = draft.trim() == bambuSalt.trim()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .testTag("$testTag-header"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Advanced",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
        }
        if (expanded) {
            Text(
                text = "Snapmaker tags read out of the box. If you already have " +
                    "a Bambu Lab tag key, paste it here to read those tags too.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("$testTag-field"),
                label = { Text("Bambu Lab tag key") },
                placeholder = { Text("Optional") },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                shape = RoundedCornerShape(20.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { onBambuSaltSaved(draft) },
                    enabled = !saved,
                    modifier = Modifier
                        .wrapContentWidth()
                        .testTag("$testTag-save"),
                ) {
                    Text(if (saved) "Saved" else "Save")
                }
            }
        }
    }
}
