package com.spoolpainter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spoolpainter.app.domain.models.Brand

/**
 * Brand picker styled to match v1's BrandSelector. "Other" reveals an inline
 * custom-name field; the typed value is forwarded via [onCustomNameChange].
 *
 * The [brands] list is the merged set from MaterialBrandRepository
 * (presets ∪ Spoolman vendors), so vendors created in Spoolman appear here
 * automatically once `refresh()` runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandPicker(
    selected: Brand?,
    customName: String,
    enabled: Boolean,
    brands: List<String>,
    onSelect: (Brand?) -> Unit,
    onCustomNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Keep the "Other" action nearest the anchor field whichever way the menu
    // opens (see rememberDropdownDirection).
    val direction = rememberDropdownDirection()
    val displayValue = selected?.name ?: ""
    val isOther = displayValue == "Other"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .testTag("main-form-brand"),
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
                label = { Text("Brand") },
                trailingIcon = if (enabled) {
                    { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                } else null,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .then(direction.anchorModifier),
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
            if (enabled) {
                val hasOther = brands.any { it.equals("Other", ignoreCase = true) }
                val otherRow: @Composable () -> Unit = {
                    if (hasOther) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "Other",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelect(Brand("Other"))
                            },
                        )
                    }
                }
                val brandRows: @Composable () -> Unit = {
                    brands
                        .filterNot { it.equals("Other", ignoreCase = true) }
                        .forEach { brand ->
                            DropdownMenuItem(
                                text = { Text(brand) },
                                onClick = {
                                    expanded = false
                                    onSelect(Brand(brand))
                                },
                            )
                        }
                }
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)),
                ) {
                    if (direction.opensUpward) {
                        brandRows()
                        if (hasOther) HorizontalDivider()
                        otherRow()
                    } else {
                        otherRow()
                        if (hasOther) HorizontalDivider()
                        brandRows()
                    }
                }
            }
        }

        if (isOther) {
            OutlinedTextField(
                value = customName,
                onValueChange = { input ->
                    val sanitized = input.take(32)
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
