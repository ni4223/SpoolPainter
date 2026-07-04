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
import com.spoolpainter.app.domain.models.Brand

/**
 * Brand picker styled to match v1's BrandSelector. "Other" reveals an inline
 * custom-name field; the typed value is forwarded via [onCustomNameChange].
 *
 * "Other" is a pinned action (see [PinnedActionMenu]) so it stays adjacent to
 * the field whichever way the menu opens — the Brand list runs 30+ vendors, so
 * without pinning "Other" scrolls off the bottom when the menu flips up (UI-46).
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
    val anchor = rememberLazyDropdownAnchor()
    val displayValue = selected?.name ?: ""
    val isOther = displayValue == "Other"
    val hasOther = brands.any { it.equals("Other", ignoreCase = true) }
    val brandRows = remember(brands) {
        brands.filterNot { it.equals("Other", ignoreCase = true) }
    }

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
            if (enabled && hasOther) {
                PinnedActionMenu(
                    expanded = expanded,
                    items = brandRows,
                    anchor = anchor,
                    onDismiss = { expanded = false },
                    onItemClick = { brand ->
                        expanded = false
                        onSelect(Brand(brand))
                    },
                    itemKey = { it },
                    itemContent = { brand -> Text(brand) },
                    pinnedContent = {
                        PinnedOtherAction(label = "Other") {
                            expanded = false
                            onSelect(Brand("Other"))
                        }
                    },
                )
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
