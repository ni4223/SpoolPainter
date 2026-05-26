package com.spoolpainter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * Color picker styled to match v1's ColorSelector. Eight named-color shortcuts
 * with circular swatches, plus a hex-entry option that accepts uppercase
 * 6-character hex codes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPicker(
    colorHex: String?,
    enabled: Boolean,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var hexInput by remember(colorHex) { mutableStateOf(colorHex.orEmpty()) }
    val current = colorHex.orEmpty()
    val displayValue = COMMON_COLORS.entries.firstOrNull { it.value.equals(current, ignoreCase = true) }
        ?.key ?: current.ifEmpty { "" }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
            .fillMaxWidth()
            .testTag("main-form-color"),
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Color") },
            leadingIcon = {
                val parsed = parseColor(current)
                if (parsed != null) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(parsed)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            shape = RoundedCornerShape(20.dp),
        )

        if (enabled) {
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = {
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { newValue ->
                                val filtered = newValue
                                    .filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
                                    .take(6)
                                    .uppercase()
                                hexInput = filtered
                                if (filtered.length == 6) {
                                    onChange(filtered)
                                }
                            },
                            label = { Text("Hex") },
                            placeholder = { Text(current.ifEmpty { "FF0000" }) },
                            prefix = { Text("#") },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                            shape = RoundedCornerShape(20.dp),
                        )
                    },
                    onClick = {},
                )

                Divider()

                COMMON_COLORS.forEach { (name, hex) ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clip(CircleShape)
                                        .background(parseColor(hex)!!)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                )
                                Text(name)
                            }
                        },
                        onClick = {
                            onChange(hex)
                            hexInput = hex
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private val COMMON_COLORS = linkedMapOf(
    "White" to "FFFFFF",
    "Red" to "FF0000",
    "Blue" to "0000FF",
    "Green" to "00FF00",
    "Yellow" to "FFFF00",
    "Orange" to "FFA500",
    "Pink" to "FFC0CB",
    "Black" to "000000",
)

private fun parseColor(hex: String?): Color? {
    if (hex == null || hex.length != 6) return null
    return try {
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        Color(red = r, green = g, blue = b)
    } catch (_: NumberFormatException) {
        null
    }
}
