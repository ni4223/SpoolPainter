package com.spoolpainter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.ui.screens.main.FormState
import com.spoolpainter.app.ui.screens.main.WeightMethod

/**
 * Sealed event interface for FilamentForm changes. All field changes (and the
 * Save button tap) are funnelled through a single [onChange] callback so the
 * caller (MainScreen → MainViewModel) can route them to the right setter.
 */
sealed interface FormChange {
    data class MaterialPicked(val value: Material?) : FormChange
    data class CustomMaterialChanged(val value: String) : FormChange
    data class BrandPicked(val value: Brand?) : FormChange
    data class CustomBrandChanged(val value: String) : FormChange
    data class ColorHex(val value: String?) : FormChange
    data class Variant(val value: String?) : FormChange
    data class TempRangesChanged(val value: TempRanges) : FormChange

    // U8-Δ-1 — filament picker
    data class FilamentSelected(val value: SpoolmanFilament?) : FormChange

    // U8-Δ-2 — More details expander. Diameter dropped in v2.0.2 (decision N).
    data object MoreDetailsToggled : FormChange
    data class EmptySpoolWeightChanged(val value: String) : FormChange
    data class PriceChanged(val value: String) : FormChange
    data class FullSpoolWeightChanged(val value: String) : FormChange
    data class DensityChanged(val value: String) : FormChange

    // U13 (Cluster A) — Spoolman-parity radio weight picker. The bidirectional
    // Remaining + Measured row + back-solve was replaced by a single active-
    // method input field; the inactive method's input is hidden.
    data class WeightMethodPicked(val value: WeightMethod) : FormChange
    data class ActiveWeightChanged(val value: String) : FormChange
}

/**
 * Filament editor — layout matches v1: Material → Variant → Color → Brand →
 * Temperature card. The Save & Write button sits at the bottom and is gated
 * by [canSave]. When [enabled] is false (e.g., during a write), every field
 * goes read-only and the Save button is hidden.
 */
@Composable
fun FilamentForm(
    state: FormState,
    customMaterial: String,
    customBrand: String,
    enabled: Boolean,
    /**
     * Locks the Material + Brand pickers. v2.1 narrowed this from the
     * v2.0.2 set (Material + Brand + Color) — Color now PATCHes the
     * filament record alongside variant on the existing-spool path, so
     * keeping it editable is safe. Material + Brand stay locked because
     * changing them means "wrong filament picked"; the user should pick a
     * different filament instead.
     */
    identityLocked: Boolean,
    spoolmanConfigured: Boolean,
    spoolmanReachable: Boolean,
    filaments: List<SpoolmanFilament>,
    materials: List<Material>,
    brands: List<String>,
    filamentSortKey: FilamentSortKey,
    filamentSortDirection: SortDirection,
    onChange: (FormChange) -> Unit,
    modifier: Modifier = Modifier,
    scanSuggestedFilamentIds: List<Int> = emptyList(),
) {
    val identityEnabled = enabled && !identityLocked
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("main-filament-form"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (spoolmanConfigured) {
            FilamentSection(
                filaments = filaments,
                selectedFilamentId = state.selectedFilamentId,
                selectedSpoolId = state.selectedSpoolId,
                enabled = enabled && spoolmanReachable,
                sortKey = filamentSortKey,
                sortDirection = filamentSortDirection,
                onSelect = { onChange(FormChange.FilamentSelected(it)) },
                scanSuggestedFilamentIds = scanSuggestedFilamentIds,
            )
        }

        MaterialPicker(
            selected = state.material,
            customName = customMaterial,
            enabled = identityEnabled,
            materials = materials,
            onSelect = { onChange(FormChange.MaterialPicked(it)) },
            onCustomNameChange = { onChange(FormChange.CustomMaterialChanged(it)) },
        )

        VariantField(
            value = state.variant,
            enabled = enabled,
            onChange = { onChange(FormChange.Variant(it)) },
        )

        ColorPicker(
            colorHex = state.colorHex,
            enabled = enabled,
            onChange = { onChange(FormChange.ColorHex(it)) },
        )

        BrandPicker(
            selected = state.brand,
            customName = customBrand,
            enabled = identityEnabled,
            brands = brands,
            onSelect = { onChange(FormChange.BrandPicked(it)) },
            onCustomNameChange = { onChange(FormChange.CustomBrandChanged(it)) },
        )
    }
}

/**
 * U13 — Spoolman-only Save button (replaces the old `Save & Write` combo).
 * Lives at the bottom of the outer Card on MainScreen. Hidden in
 * [com.spoolpainter.app.ui.screens.main.WriteMode.RawNoUrl] (no Spoolman
 * target). Label locked to "Save to Spoolman" per Q-U13-4=A.
 */
@Composable
fun SaveToSpoolmanButton(
    enabled: Boolean,
    onClick: () -> Unit,
    label: String = "Save to Spoolman",
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("main-form-save"),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun VariantField(
    value: String?,
    enabled: Boolean,
    onChange: (String?) -> Unit,
) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = { input ->
            // Sanitisation: alphanumeric + common label punctuation, max 50
            // chars (UI-50 Ask 2 — descriptive variants like "PLA (Matte)" and
            // "PLA+" were getting stripped/cut). No forced casing — user types
            // whatever they want. Drop control chars only.
            val sanitised = input
                .filter { it.isLetterOrDigit() || it in " -+()" }
                .take(50)
            onChange(sanitised.takeIf { it.isNotBlank() })
        },
        label = { Text("Variant (Wood, Pro, HS, etc.)") },
        placeholder = { Text("Optional") },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("main-form-variant"),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        shape = RoundedCornerShape(20.dp),
    )
}
