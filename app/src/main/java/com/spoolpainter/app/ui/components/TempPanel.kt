package com.spoolpainter.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
 * Temperature card matching v1's TemperatureCard layout: a card with two rows
 * (Nozzle and Bed), each with a min and max field flanked by ±5 °C step
 * buttons. Empty input is treated as null in [TempRanges].
 */
@Composable
fun TempPanel(
    ranges: TempRanges,
    enabled: Boolean,
    onChange: (TempRanges) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("main-form-temps"),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Temperature",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            TempRows(ranges = ranges, enabled = enabled, onChange = onChange)
        }
    }
}

/**
 * Bare nozzle + bed rows without the Card / heading wrapper. Used by
 * MoreDetailsExpander so the Temperature section can sit alongside the spool
 * metadata section under one expander.
 */
@Composable
fun TempRows(
    ranges: TempRanges,
    enabled: Boolean,
    onChange: (TempRanges) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TempRow(
            label = "Nozzle",
            min = ranges.extruderMin,
            max = ranges.extruderMax,
            enabled = enabled,
            onMinChange = { onChange(ranges.copy(extruderMin = it)) },
            onMaxChange = { onChange(ranges.copy(extruderMax = it)) },
        )
        TempRow(
            label = "Bed",
            min = ranges.bedMin,
            max = ranges.bedMax,
            enabled = enabled,
            onMinChange = { onChange(ranges.copy(bedMin = it)) },
            onMaxChange = { onChange(ranges.copy(bedMax = it)) },
        )
    }
}

@Composable
private fun TempRow(
    label: String,
    min: Int?,
    max: Int?,
    enabled: Boolean,
    onMinChange: (Int?) -> Unit,
    onMaxChange: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(60.dp),
        )
        TempControl(
            value = min,
            enabled = enabled,
            onValueChange = onMinChange,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(16.dp))
        TempControl(
            value = max,
            enabled = enabled,
            onValueChange = onMaxChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TempControl(
    value: Int?,
    enabled: Boolean,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "−",
            modifier = Modifier
                .size(32.dp)
                .let {
                    if (enabled) {
                        it.clickable {
                            val current = value ?: 0
                            if (current > 0) onValueChange((current - 5).coerceAtLeast(0))
                        }
                    } else {
                        it
                    }
                }
                .wrapContentSize(Alignment.Center),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        OutlinedTextField(
            value = value?.toString().orEmpty(),
            onValueChange = { input ->
                val sanitized = input.filter { it.isDigit() }.take(3)
                val parsed = sanitized.toIntOrNull()
                if (parsed == null) {
                    onValueChange(null)
                } else if (parsed <= 500) {
                    onValueChange(parsed)
                }
            },
            modifier = Modifier.width(96.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            suffix = {
                Text(
                    text = "°C",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            shape = RoundedCornerShape(20.dp),
        )

        Text(
            text = "+",
            modifier = Modifier
                .size(32.dp)
                .let {
                    if (enabled) {
                        it.clickable {
                            val current = value ?: 0
                            if (current < 500) onValueChange((current + 5).coerceAtMost(500))
                        }
                    } else {
                        it
                    }
                }
                .wrapContentSize(Alignment.Center),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
