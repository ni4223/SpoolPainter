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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.ui.screens.main.WeightMethod

/**
 * Collapsed-by-default "Filament metadata ▾" expander hosting three labelled
 * sections: Temperature (nozzle + bed), Weight (Remaining + Measured spool-
 * scope row visible only when an existing spool is selected, plus filament
 * weight + spool weight), and Others (density + price). Empty input on a
 * Float field = null override.
 *
 * v2.0.2 lockdown (decision J): when [filamentSpecLocked] is true (existing
 * spool OR existing filament selected), filament weight / spool weight /
 * density are disabled. Price stays editable everywhere — it's spool-scope
 * (decision M). Diameter removed entirely (decision N) — defaults to 1.75mm
 * at CreateFilamentRequest time.
 */
@Composable
fun MoreDetailsExpander(
    expanded: Boolean,
    enabled: Boolean,
    spoolmanConfigured: Boolean,
    spoolmanReachable: Boolean,
    /**
     * v2.0.2 — gate for filament-scope spec fields (filament weight / spool
     * weight / density). True when selectedSpoolId != null OR selectedFilamentId
     * != null. Price is NOT gated by this — it's spool-scope.
     */
    filamentSpecLocked: Boolean,
    /**
     * v2.0.2 — controls whether the spool-scope Remaining/Measured radio
     * renders editable. True when selectedSpoolId != null.
     */
    showSpoolScopeFields: Boolean,
    tempRanges: TempRanges,
    emptySpoolWeightG: Float?,
    priceMajor: Float?,
    priceSuffix: String,
    fullSpoolWeightG: Float?,
    densityGPerCm3: Float?,
    /**
     * U13 (Cluster A) — radio-style weight picker. [weightMethod] picks which
     * single value field is rendered ([WeightMethodRadio]); the inactive
     * method's field is hidden entirely.
     *  - When method=Remaining, [activeWeightValueG] is the remaining weight (g).
     *  - When method=Measured, [activeWeightValueG] is the measured/scale weight (g).
     */
    weightMethod: WeightMethod,
    activeWeightValueG: Float?,
    onWeightMethodPicked: (WeightMethod) -> Unit,
    onActiveWeightChange: (String) -> Unit,
    onToggle: () -> Unit,
    onTempRangesChange: (TempRanges) -> Unit,
    onEmptySpoolWeightChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onFullSpoolWeightChange: (String) -> Unit,
    onDensityChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("more-details"),
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
                        val filamentSpecEnabled = spoolmanFieldsEnabled && !filamentSpecLocked
                        HorizontalDivider()
                        SectionLabel("Weight")
                        // U13 (Cluster A) — radio-style weight picker. Visible
                        // only on the existing-spool path; the inactive
                        // method's input is hidden entirely (saves vertical
                        // real estate, kills the silent-keystroke bug by
                        // construction). When no spool is selected, the
                        // radio collapses out — preview is implied by
                        // "Filament weight" below.
                        if (showSpoolScopeFields) {
                            WeightMethodRadio(
                                method = weightMethod,
                                activeValueG = activeWeightValueG,
                                emptySpoolWeightG = emptySpoolWeightG,
                                enabled = spoolmanFieldsEnabled,
                                onMethodPicked = onWeightMethodPicked,
                                onActiveValueChange = onActiveWeightChange,
                            )
                        }
                        DecimalField(
                            label = "Filament weight",
                            supportingText = if (filamentSpecLocked && showSpoolScopeFields) {
                                null
                            } else {
                                "Net filament only. Excludes the empty spool."
                            },
                            suffix = "g",
                            value = fullSpoolWeightG,
                            enabled = filamentSpecEnabled,
                            testTag = "more-details-full-spool-weight",
                            maxLength = 5,
                            onChange = onFullSpoolWeightChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Empty spool is spool-scope per Spoolman's data
                        // model (verified against models.py:46+73) — both
                        // spool and filament records carry it. Editable on
                        // any path where a spool exists or is being
                        // created; back-solved via Measured if the user
                        // doesn't know the empty-spool weight directly.
                        DecimalField(
                            label = "Empty spool",
                            supportingText = "Spool weight without filament.",
                            suffix = "g",
                            value = emptySpoolWeightG,
                            enabled = spoolmanFieldsEnabled,
                            testTag = "more-details-empty-spool-weight",
                            maxLength = 4,
                            onChange = onEmptySpoolWeightChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        HorizontalDivider()
                        SectionLabel("Others")
                        DecimalField(
                            label = "Density",
                            supportingText = null,
                            suffix = "g/cm³",
                            value = densityGPerCm3,
                            enabled = filamentSpecEnabled,
                            testTag = "more-details-density",
                            maxLength = 4,
                            onChange = onDensityChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DecimalField(
                            label = "Price",
                            supportingText = null,
                            suffix = priceSuffix,
                            value = priceMajor,
                            // v2.1: price editable on existing-spool too. Edits
                            // PATCH spool.price only (per-spool override) —
                            // filament.price stays untouched, so sibling
                            // spools aren't affected. SaveToSpoolmanUseCase
                            // routes price through patchSpoolFields with the
                            // same stale-prefill guard as remaining_weight.
                            enabled = spoolmanFieldsEnabled,
                            testTag = "more-details-price",
                            maxLength = 7,
                            onChange = onPriceChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
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

/**
 * Numeric input that holds in-flight string state locally so the user can
 * type intermediate values like `"1."` (which `Float.toFloatOrNull()` rejects)
 * without losing the keystroke. The VM only sees a value when the string
 * parses cleanly to Float; empty input commits null (clear).
 *
 * Sanitisation: digits + at most ONE `.`; per-field [maxLength] cap.
 *
 * `key(value)` resets the local string when the parent reassigns the Float —
 * prefill flows + onSpoolSelected re-emit a different value, and we want the
 * field to reflect that. While the user is typing, parent and local stay in
 * sync via the `onChange` callback.
 */
@Composable
private fun DecimalField(
    label: String,
    supportingText: String?,
    suffix: String,
    value: Float?,
    enabled: Boolean,
    testTag: String,
    maxLength: Int,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    disabledAlpha: Float = 0.7f,
) {
    var text by remember(value) {
        mutableStateOf(value?.let { formatFloat(it) } ?: "")
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            if (input.length > maxLength) return@OutlinedTextField
            // Keep digits + only the FIRST '.' (drop any subsequent dots).
            val firstDot = input.indexOf('.')
            val sanitised = input.filterIndexed { i, c ->
                c.isDigit() || (c == '.' && i == firstDot)
            }
            text = sanitised
            when {
                sanitised.isEmpty() -> onChange("")
                sanitised.toFloatOrNull() != null -> onChange(sanitised)
                // Intermediate state ("1.", ".5") — show locally, don't
                // commit until it parses.
            }
        },
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text("Optional") },
        suffix = { Text(suffix) },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
            .testTag(testTag),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            // UX 2026-05-31: dim the whole field uniformly to ~30% opacity.
            // No container swap — the field stays the same shape; just
            // greyer. Reads as "off" without looking like a different kind
            // of element.
            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha),
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha),
            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = disabledAlpha),
            disabledSuffixColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha),
            disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha),
        ),
        shape = RoundedCornerShape(20.dp),
    )
}

private fun formatFloat(v: Float): String {
    // Drop trailing .0 for whole numbers ("1000.0" -> "1000"); preserve fraction.
    return if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()
}
