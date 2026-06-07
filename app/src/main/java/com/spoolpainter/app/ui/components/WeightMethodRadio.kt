package com.spoolpainter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import com.spoolpainter.app.ui.screens.main.WeightMethod
import kotlin.math.max

/**
 * U13 (Cluster A) — Spoolman-parity weight picker. Two-radio segmented row at
 * the top picks the active method; a single [DecimalField] below renders for
 * the active method only — the inactive method's input is HIDDEN entirely
 * (saves vertical real estate, eliminates silent-keystroke-loss by
 * construction since only one source of truth exists at a time).
 *
 *  - method=Remaining → "Remaining" `[ ] g` field. supportingText shows
 *    "Scale will read N g" when [emptySpoolWeightG] + active value are both
 *    set.
 *  - method=Measured → "Measured" `[ ] g` field. supportingText shows
 *    "Filament left: N g" (clamped ≥ 0) when the conversion is computable.
 *
 * State holding: [activeValueG] is the value in the units the user is
 * currently entering (remaining when Remaining, measured when Measured); the
 * caller derives it from FormState. The composable doesn't read or write the
 * inactive method's field — that lives entirely in the ViewModel.
 */
@Composable
fun WeightMethodRadio(
    method: WeightMethod,
    activeValueG: Float?,
    emptySpoolWeightG: Float?,
    enabled: Boolean,
    onMethodPicked: (WeightMethod) -> Unit,
    onActiveValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weight-method-radio"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("weight-method-radio-row"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioOption(
                label = "Remaining",
                selected = method == WeightMethod.Remaining,
                enabled = enabled,
                onClick = { onMethodPicked(WeightMethod.Remaining) },
                testTag = "weight-method-radio-remaining",
                modifier = Modifier.weight(1f),
            )
            RadioOption(
                label = "Measured",
                selected = method == WeightMethod.Measured,
                enabled = enabled,
                onClick = { onMethodPicked(WeightMethod.Measured) },
                testTag = "weight-method-radio-measured",
                modifier = Modifier.weight(1f),
            )
        }
        val (label, supportingText, fieldTag) = when (method) {
            WeightMethod.Remaining -> Triple(
                "Remaining",
                if (emptySpoolWeightG != null && activeValueG != null) {
                    "Spool on scale: ${formatGrams(activeValueG + emptySpoolWeightG)} g"
                } else null,
                "weight-method-remaining-input",
            )
            WeightMethod.Measured -> Triple(
                "Measured",
                if (emptySpoolWeightG != null && activeValueG != null) {
                    "Filament left: ${formatGrams(max(0f, activeValueG - emptySpoolWeightG))} g"
                } else null,
                "weight-method-measured-input",
            )
        }
        ActiveValueField(
            label = label,
            value = activeValueG,
            supportingText = supportingText,
            enabled = enabled,
            testTag = fieldTag,
            onChange = onActiveValueChange,
        )
    }
}

@Composable
private fun RadioOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    androidx.compose.material3.Surface(
        modifier = modifier
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .testTag(testTag),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                else androidx.compose.ui.text.font.FontWeight.Normal,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun ActiveValueField(
    label: String,
    value: Float?,
    supportingText: String?,
    enabled: Boolean,
    testTag: String,
    onChange: (String) -> Unit,
) {
    var text by remember(value, label) {
        mutableStateOf(value?.let { formatGrams(it) } ?: "")
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            if (input.length > 5) return@OutlinedTextField
            val firstDot = input.indexOf('.')
            val sanitised = input.filterIndexed { i, c ->
                c.isDigit() || (c == '.' && i == firstDot)
            }
            text = sanitised
            when {
                sanitised.isEmpty() -> onChange("")
                sanitised.toFloatOrNull() != null -> onChange(sanitised)
                // intermediate ("1.", ".5") — local state only
            }
        },
        enabled = enabled,
        label = { Text(label) },
        suffix = { Text("g") },
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
            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            disabledSuffixColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        ),
        shape = RoundedCornerShape(20.dp),
    )
}

private fun formatGrams(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()
