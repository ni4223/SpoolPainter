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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.spoolpainter.app.domain.models.TempRanges

/**
 * Collapsed-by-default "Filament details ▾" expander hosting two labelled
 * sections: Temperature (nozzle + bed) and Spool (filament weight, spool
 * weight, diameter, density, price). Empty input = null override on the
 * spool fields (use stored value or call-site default).
 */
@Composable
fun MoreDetailsExpander(
    expanded: Boolean,
    enabled: Boolean,
    spoolmanConfigured: Boolean,
    spoolmanReachable: Boolean,
    tempRanges: TempRanges,
    emptySpoolWeightG: Float?,
    priceMajor: Float?,
    priceSuffix: String,
    fullSpoolWeightG: Float?,
    diameterMm: Float?,
    densityGPerCm3: Float?,
    onToggle: () -> Unit,
    onTempRangesChange: (TempRanges) -> Unit,
    onEmptySpoolWeightChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onFullSpoolWeightChange: (String) -> Unit,
    onDiameterChange: (String) -> Unit,
    onDensityChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("more-details"),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, onClick = onToggle)
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SectionLabel("Temperature")
                    TempRows(
                        ranges = tempRanges,
                        enabled = enabled,
                        onChange = onTempRangesChange,
                    )
                    if (spoolmanConfigured) {
                        val spoolmanFieldsEnabled = enabled && spoolmanReachable
                        HorizontalDivider()
                        SectionLabel("Weight")
                        DecimalField(
                            label = "Filament weight",
                            supportingText = "Net filament only. Excludes the empty spool.",
                            suffix = "g",
                            value = fullSpoolWeightG,
                            enabled = spoolmanFieldsEnabled,
                            testTag = "more-details-full-spool-weight",
                            onChange = onFullSpoolWeightChange,
                        )
                        DecimalField(
                            label = "Spool weight",
                            supportingText = "Empty spool only.",
                            suffix = "g",
                            value = emptySpoolWeightG,
                            enabled = spoolmanFieldsEnabled,
                            testTag = "more-details-empty-spool-weight",
                            onChange = onEmptySpoolWeightChange,
                        )
                        HorizontalDivider()
                        SectionLabel("Others")
                        DecimalField(
                            label = "Diameter",
                            supportingText = null,
                            suffix = "mm",
                            value = diameterMm,
                            enabled = spoolmanFieldsEnabled,
                            testTag = "more-details-diameter",
                            onChange = onDiameterChange,
                        )
                        DecimalField(
                            label = "Density",
                            supportingText = null,
                            suffix = "g/cm³",
                            value = densityGPerCm3,
                            enabled = spoolmanFieldsEnabled,
                            testTag = "more-details-density",
                            onChange = onDensityChange,
                        )
                        DecimalField(
                            label = "Price",
                            supportingText = null,
                            suffix = priceSuffix,
                            value = priceMajor,
                            enabled = spoolmanFieldsEnabled,
                            testTag = "more-details-price",
                            onChange = onPriceChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
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
