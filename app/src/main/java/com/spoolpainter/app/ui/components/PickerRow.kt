package com.spoolpainter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Two-line dropdown row used by Spool + Filament pickers when the menu
 * is open. Left: 24dp color swatch (or a "no color" outline if hex is
 * null). Right: bold first line ("Vendor · Name") + faded second line
 * ("Material · Variant · #id"). Names already get long since users add
 * variant/grade text to filament names — keeping vendor and name on the
 * primary line, with the secondary metadata muted, lets the user scan
 * by colour first, then by vendor/name.
 */
@Composable
fun PickerRow(
    primary: String,
    secondary: String,
    colorHex: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ColorSwatch(colorHex)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = primary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondary.isNotBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(colorHex: String?) {
    // Cache the parsed color per hex string. ExposedDropdownMenu renders
    // every row eagerly (no lazy column), so any repeat work in the row
    // multiplies by N spools. Caching the hex→Color parse is a measurable
    // win on long lists.
    val parsed = remember(colorHex) { parseSwatchColor(colorHex) }
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(parsed ?: surfaceVariant),
    )
}

private fun parseSwatchColor(hex: String?): Color? {
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
