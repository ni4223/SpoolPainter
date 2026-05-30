package com.spoolpainter.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * U8-Δ-2 — collapsed-by-default "More details ▾" expander hosting five
 * filament-scope metadata override fields: empty spool weight, price,
 * full spool weight, diameter, density. Empty input = null override
 * (use stored value or call-site default). Independent of the filament
 * expander (Q-U8-18=A).
 */
@Composable
fun MoreDetailsExpander(
    expanded: Boolean,
    enabled: Boolean,
    emptySpoolWeightG: Float?,
    priceMajor: Float?,
    priceSuffix: String,
    fullSpoolWeightG: Float?,
    diameterMm: Float?,
    densityGPerCm3: Float?,
    onToggle: () -> Unit,
    onEmptySpoolWeightChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onFullSpoolWeightChange: (String) -> Unit,
    onDiameterChange: (String) -> Unit,
    onDensityChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("more-details"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(vertical = 8.dp)
                .testTag("more-details-header"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse filament metadata" else "Expand filament metadata",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Filament metadata",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DecimalField(
                    label = "Filament weight",
                    supportingText = "The filament weight of a full spool (net weight). This should not include the weight of the spool itself.",
                    suffix = "g",
                    value = fullSpoolWeightG,
                    enabled = enabled,
                    testTag = "more-details-full-spool-weight",
                    onChange = onFullSpoolWeightChange,
                )
                DecimalField(
                    label = "Spool weight",
                    supportingText = "The weight of an empty spool.",
                    suffix = "g",
                    value = emptySpoolWeightG,
                    enabled = enabled,
                    testTag = "more-details-empty-spool-weight",
                    onChange = onEmptySpoolWeightChange,
                )
                DecimalField(
                    label = "Price",
                    supportingText = null,
                    suffix = priceSuffix,
                    value = priceMajor,
                    enabled = enabled,
                    testTag = "more-details-price",
                    onChange = onPriceChange,
                )
                DecimalField(
                    label = "Diameter",
                    supportingText = null,
                    suffix = "mm",
                    value = diameterMm,
                    enabled = enabled,
                    testTag = "more-details-diameter",
                    onChange = onDiameterChange,
                )
                DecimalField(
                    label = "Density",
                    supportingText = null,
                    suffix = "g/cm³",
                    value = densityGPerCm3,
                    enabled = enabled,
                    testTag = "more-details-density",
                    onChange = onDensityChange,
                )
            }
        }
    }
}

@Composable
private fun DecimalField(
    label: String,
    supportingText: String?,
    suffix: String,
    value: Float?,
    enabled: Boolean,
    testTag: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value?.let { formatFloat(it) } ?: "",
        onValueChange = { input ->
            // Allow digits + at most one '.'
            val sanitised = input.filter { it.isDigit() || it == '.' }
            onChange(sanitised)
        },
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text("Optional") },
        suffix = { Text(suffix) },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        shape = RoundedCornerShape(20.dp),
    )
}

private fun formatFloat(v: Float): String {
    // Drop trailing .0 for whole numbers ("1000.0" -> "1000"); preserve fraction.
    return if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()
}
