package com.spoolpainter.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView

/**
 * Tracks whether an [androidx.compose.material3.ExposedDropdownMenu] anchored to
 * a field will open upward instead of downward.
 *
 * ExposedDropdownMenu flips upward when there isn't enough room below the anchor
 * before the window bottom. Pickers that put a high-value action row (e.g.
 * "Other", "Color Wheel") at the top of the menu want that row nearest the
 * field in either direction, so they reorder based on this flag.
 *
 * Usage:
 * ```
 * val direction = rememberDropdownDirection()
 * OutlinedTextField(modifier = Modifier.menuAnchor().then(direction.anchorModifier), ...)
 * ExposedDropdownMenu(...) {
 *     if (direction.opensUpward) { rows(); actionRow() } else { actionRow(); rows() }
 * }
 * ```
 */
class DropdownDirection internal constructor(
    val opensUpward: Boolean,
    val anchorModifier: Modifier,
)

@Composable
fun rememberDropdownDirection(): DropdownDirection {
    val viewHeightPx = LocalView.current.height
    var opensUpward by remember { mutableStateOf(false) }
    val anchorModifier = Modifier.onGloballyPositioned { coords ->
        val bounds = coords.boundsInWindow()
        // Menu flips up when the space below the field is smaller than above.
        opensUpward = (viewHeightPx - bounds.bottom) < bounds.top
    }
    return DropdownDirection(opensUpward = opensUpward, anchorModifier = anchorModifier)
}
