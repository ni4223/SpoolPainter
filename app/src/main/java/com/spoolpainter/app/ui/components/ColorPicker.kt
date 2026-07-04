package com.spoolpainter.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Color picker carried forward from v1's ColorSelector. Three entry points:
 *   1. "No Color" — clears the field (variant attribute, not always set).
 *   2. Eight named-color shortcuts with circular swatches.
 *   3. "Color Wheel" — opens a modal dialog with HSV picker + brightness slider
 *      + hex entry; pressing Cancel restores the colour the dialog opened with.
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
    var showWheel by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var wheelOriginalColor by remember { mutableStateOf<String?>(null) }
    // The action row (Color Wheel / Scan color) is pinned nearest the anchor
    // field so it's always reachable whichever way the menu opens (see
    // PinnedActionMenu).
    val anchor = rememberLazyDropdownAnchor()
    val current = colorHex.orEmpty()
    // Empty when nothing's selected — let the placeholder show. "No Color" is
    // a deliberate menu choice for clearing an existing selection, not the
    // initial display state.
    val displayValue = if (current.isEmpty()) {
        ""
    } else {
        COMMON_COLORS.entries.firstOrNull { it.value.equals(current, ignoreCase = true) }
            ?.key ?: current
    }

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
            placeholder = { Text("Pick a color") },
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
                } else {
                    NoColorIcon(size = 24.dp)
                }
            },
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
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            ),
            shape = RoundedCornerShape(20.dp),
        )

        if (enabled) {
            PinnedActionMenu(
                expanded = expanded,
                items = COMMON_COLOR_ROWS,
                anchor = anchor,
                onDismiss = { expanded = false },
                onItemClick = { row ->
                    onChange(row.hex)
                    expanded = false
                },
                itemKey = { it.name },
                itemContent = { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(parseColor(row.hex)!!)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        )
                        Text(row.name)
                    }
                },
                pinnedContent = {
                    // Color Wheel (row body) + Scan color (own tap target) share
                    // the pinned action row. The camera Row consumes its own tap
                    // so the wheel onClick doesn't also fire.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                wheelOriginalColor = colorHex
                                showWheel = true
                                expanded = false
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Color Wheel",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showCamera = true
                                    expanded = false
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("main-form-color-camera"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Scan color",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        }
    }

    if (showWheel) {
        ColorWheelDialog(
            initialColor = colorHex ?: "FF0000",
            onPreview = onChange,
            onConfirm = { showWheel = false },
            onCancel = {
                onChange(wheelOriginalColor)
                showWheel = false
            },
        )
    }

    if (showCamera) {
        CameraColorSampler(
            onPick = { hex ->
                onChange(hex)
                showCamera = false
            },
            onDismiss = { showCamera = false },
        )
    }
}

@Composable
private fun NoColorIcon(size: Dp) {
    val outlineColor = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, outlineColor, CircleShape),
    ) {
        drawLine(
            color = outlineColor,
            start = Offset(this.size.width * 0.15f, this.size.height * 0.85f),
            end = Offset(this.size.width * 0.85f, this.size.height * 0.15f),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorWheelDialog(
    initialColor: String,
    onPreview: (String?) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    var hexInput by remember(initialColor) { mutableStateOf(initialColor) }

    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Color Wheel",
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { newValue ->
                            val filtered = newValue
                                .filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
                                .take(6)
                                .uppercase()
                            hexInput = filtered
                            if (filtered.length == 6) onPreview(filtered)
                        },
                        label = { Text("Hex") },
                        prefix = { Text("#") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        shape = RoundedCornerShape(20.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(48.dp)
                            .background(
                                parseColor(hexInput) ?: Color.Transparent,
                                RoundedCornerShape(12.dp),
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp),
                            ),
                    )
                }
                ColorWheel(
                    seedColor = hexInput.takeIf { it.length == 6 } ?: "FF0000",
                    onColorChanged = { hex ->
                        hexInput = hex
                        onPreview(hex)
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Cancel") }
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun ColorWheel(
    seedColor: String,
    onColorChanged: (String) -> Unit,
) {
    var hue by remember { mutableStateOf(0f) }
    var saturation by remember { mutableStateOf(1f) }
    var brightness by remember { mutableStateOf(1f) }

    LaunchedEffect(seedColor) {
        val parsed = runCatching { android.graphics.Color.parseColor("#$seedColor") }.getOrNull()
            ?: return@LaunchedEffect
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(parsed, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
    }

    fun emit() {
        val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
        onColorChanged(String.format("%06X", 0xFFFFFF and color))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .size(180.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val delta = offset - center
                                val radius = minOf(size.width, size.height) / 2f
                                hue = (atan2(delta.y, delta.x) * 180 / PI + 360).toFloat() % 360f
                                saturation = (sqrt(delta.x * delta.x + delta.y * delta.y) / radius)
                                    .coerceIn(0f, 1f)
                                emit()
                            },
                        ) { change, _ ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val delta = change.position - center
                            val radius = minOf(size.width, size.height) / 2f
                            hue = (atan2(delta.y, delta.x) * 180 / PI + 360).toFloat() % 360f
                            saturation = (sqrt(delta.x * delta.x + delta.y * delta.y) / radius)
                                .coerceIn(0f, 1f)
                            emit()
                        }
                    },
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = minOf(size.width, size.height) / 2

                for (angle in 0..360 step 2) {
                    for (r in 0..radius.toInt() step 4) {
                        val sat = r / radius
                        val color = Color.hsv(angle.toFloat(), sat, brightness)
                        val x = center.x + r * cos(angle * PI / 180).toFloat()
                        val y = center.y + r * sin(angle * PI / 180).toFloat()
                        drawCircle(color, 2.dp.toPx(), Offset(x, y))
                    }
                }

                val selectorR = saturation * radius
                val sx = center.x + selectorR * cos(hue * PI / 180).toFloat()
                val sy = center.y + selectorR * sin(hue * PI / 180).toFloat()
                drawCircle(
                    Color.White,
                    12.dp.toPx(),
                    Offset(sx, sy),
                    style = Stroke(3.dp.toPx()),
                )
                drawCircle(
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))),
                    8.dp.toPx(),
                    Offset(sx, sy),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Brightness", style = MaterialTheme.typography.labelLarge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.Black, RoundedCornerShape(4.dp)),
                )
                Slider(
                    value = brightness,
                    onValueChange = {
                        brightness = it
                        emit()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                )
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

@androidx.compose.runtime.Immutable
private data class ColorRow(val name: String, val hex: String)

private val COMMON_COLOR_ROWS = COMMON_COLORS.map { (name, hex) -> ColorRow(name, hex) }

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
