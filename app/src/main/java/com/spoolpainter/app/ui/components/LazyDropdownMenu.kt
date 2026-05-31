package com.spoolpainter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * Lazy-rendering replacement for ExposedDropdownMenu. Material 3's
 * ExposedDropdownMenu wraps a DropdownMenu which composes ALL items
 * eagerly — at 50+ rows with a multi-composable PickerRow that's a
 * visible tap-to-open lag (~half-second on mid-range hardware). This
 * uses a Popup + LazyColumn so only on-screen rows compose.
 *
 * Anchored via [anchorModifier]: callers spread it across the anchor
 * (the OutlinedTextField wrapping its menuAnchor) so this component can
 * position the popup at its bottom edge with matching width. The
 * caller's layout owns dismiss-on-outside-tap via PopupProperties below.
 *
 * Usage:
 * ```
 * val anchor = rememberLazyDropdownAnchor()
 * ExposedDropdownMenuBox(...) {
 *     OutlinedTextField(modifier = Modifier.menuAnchor().then(anchor.modifier))
 *     LazyDropdownMenu(expanded, items, anchor = anchor, ...)
 * }
 * ```
 */
@Composable
fun <T : Any> LazyDropdownMenu(
    expanded: Boolean,
    items: List<T>,
    anchor: LazyDropdownAnchor,
    onDismiss: () -> Unit,
    onItemClick: (T) -> Unit,
    itemKey: (T) -> Any,
    itemContent: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    maxHeightDp: Int = 320,
) {
    if (!expanded || items.isEmpty()) return
    val density = LocalDensity.current
    val anchorBounds = anchor.bounds ?: return
    val anchorWidthDp = with(density) { anchorBounds.width.toDp() }
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        popupPositionProvider = AnchorBelowPositionProvider(
            anchorBounds = anchorBounds,
            yOffsetPx = with(density) { 2.dp.roundToPx() },
        ),
    ) {
        Box(
            modifier = modifier
                .width(anchorWidthDp)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeightDp.dp),
            ) {
                items(items = items, key = itemKey) { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        itemContent(item)
                    }
                }
            }
        }
    }
}

/** Holder for the anchor's measured global bounds. */
class LazyDropdownAnchor internal constructor() {
    internal var bounds: IntRect? by mutableStateOf(null)
    val modifier: Modifier
        get() = Modifier.onGloballyPositioned { coords ->
            val pos = coords.positionInWindow()
            val size = coords.size
            bounds = IntRect(
                left = pos.x.toInt(),
                top = pos.y.toInt(),
                right = pos.x.toInt() + size.width,
                bottom = pos.y.toInt() + size.height,
            )
        }
}

@Composable
fun rememberLazyDropdownAnchor(): LazyDropdownAnchor =
    remember { LazyDropdownAnchor() }

private class AnchorBelowPositionProvider(
    private val anchorBounds: IntRect,
    private val yOffsetPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds_: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // Place popup at the anchor's left edge, just below its bottom.
        // If it would clip the bottom of the window, flip above the anchor.
        val belowY = anchorBounds.bottom + yOffsetPx
        val popupBottom = belowY + popupContentSize.height
        val finalY = if (popupBottom <= windowSize.height) {
            belowY
        } else {
            (anchorBounds.top - yOffsetPx - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x = anchorBounds.left, y = finalY)
    }
}
