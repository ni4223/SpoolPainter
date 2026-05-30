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
    data object FilamentSectionToggled : FormChange

    // U8-Δ-2 — More details expander
    data object MoreDetailsToggled : FormChange
    data class EmptySpoolWeightChanged(val value: String) : FormChange
    data class PriceChanged(val value: String) : FormChange
    data class FullSpoolWeightChanged(val value: String) : FormChange
    data class DiameterChanged(val value: String) : FormChange
    data class DensityChanged(val value: String) : FormChange
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
    filaments: List<SpoolmanFilament>,
    materials: List<Material>,
    brands: List<String>,
    filamentSortKey: FilamentSortKey,
    filamentSortDirection: SortDirection,
    onChange: (FormChange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("main-filament-form"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FilamentSectionExpander(
            filaments = filaments,
            selectedFilamentId = state.selectedFilamentId,
            expanded = state.filamentSectionExpanded,
            enabled = enabled,
            sortKey = filamentSortKey,
            sortDirection = filamentSortDirection,
            onToggle = { onChange(FormChange.FilamentSectionToggled) },
            onSelect = { onChange(FormChange.FilamentSelected(it)) },
        )

        MaterialPicker(
            selected = state.material,
            customName = customMaterial,
            enabled = enabled,
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
            enabled = enabled,
            brands = brands,
            onSelect = { onChange(FormChange.BrandPicked(it)) },
            onCustomNameChange = { onChange(FormChange.CustomBrandChanged(it)) },
        )
    }
}

@Composable
fun SaveAndWriteButton(
    label: String,
    canSave: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = canSave,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("main-form-save"),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimary,
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
            // v1 sanitisation: alphanumeric + spaces + hyphens, max 25 chars,
            // first char title-cased.
            val sanitised = input.filter { it.isLetterOrDigit() || it in " -" }
                .take(25)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
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
