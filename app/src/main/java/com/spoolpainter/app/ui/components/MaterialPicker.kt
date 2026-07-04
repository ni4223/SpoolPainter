package com.spoolpainter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spoolpainter.app.domain.models.Material

/**
 * Material picker styled to match v1's MaterialSelector. Selecting a known
 * material from the dropdown invokes [onSelect]; choosing the "Other" entry
 * opens an inline custom-name field, with the typed name passed through
 * [onCustomNameChange].
 *
 * "Other" is a pinned action (see [PinnedActionMenu]) so it stays adjacent to
 * the field whichever way the menu opens, without scrolling away on a long list
 * (UI-46).
 *
 * The [materials] list is the merged set from MaterialBrandRepository
 * (presets ∪ distinct material strings on Spoolman filaments), so custom
 * materials the user has saved appear here automatically once `refresh()`
 * runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialPicker(
    selected: Material?,
    customName: String,
    enabled: Boolean,
    materials: List<Material>,
    onSelect: (Material?) -> Unit,
    onCustomNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val anchor = rememberLazyDropdownAnchor()
    val displayValue = selected?.name ?: ""
    val isOther = displayValue == "Other"
    val other = materials.firstOrNull { it.name.equals("Other", ignoreCase = true) }
    val materialRows = remember(materials) {
        materials.filterNot { it.name.equals("Other", ignoreCase = true) }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .testTag("main-form-material"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = !expanded },
            modifier = if (isOther) Modifier.width(120.dp) else Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = displayValue,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text("Material") },
                trailingIcon = if (enabled) {
                    { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                } else null,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .then(anchor.modifier),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                ),
                shape = RoundedCornerShape(20.dp),
            )
            if (enabled && other != null) {
                PinnedActionMenu(
                    expanded = expanded,
                    items = materialRows,
                    anchor = anchor,
                    onDismiss = { expanded = false },
                    onItemClick = { material ->
                        expanded = false
                        onSelect(material)
                    },
                    itemKey = { it.name },
                    itemContent = { material -> Text(material.name) },
                    pinnedContent = {
                        PinnedOtherAction(label = other.name) {
                            expanded = false
                            onSelect(other)
                        }
                    },
                )
            }
        }

        if (isOther) {
            OutlinedTextField(
                value = customName,
                onValueChange = { input ->
                    val sanitized = input.filter { it.isLetterOrDigit() || it in "-+" }
                        .take(8)
                        .uppercase()
                    onCustomNameChange(sanitized)
                },
                label = { Text("Custom") },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                shape = RoundedCornerShape(20.dp),
            )
        }
    }
}
