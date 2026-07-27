package com.spoolpainter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
// (items() replaced by itemsIndexed() for the U20 divider)
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    // U20 — opt-in two-group render. When supplied, a thin divider is drawn
    // immediately before the first item for which this returns true, so a
    // caller can float a "suggested" group to the top and separate it from the
    // rest with no header label (Q-U20-1). Omit for today's flat list.
    dividerBefore: ((T) -> Boolean)? = null,
    // U21 (UI-48) — opt-in sticky header, e.g. a type-to-search field. Pinned
    // above the rows and stays visible while the list scrolls. When a header is
    // present the popup stays open even with zero items (a query filtered
    // everything out) and shows a non-clickable "No matches" row so the user can
    // edit their query. Omit for today's behavior (empty items renders nothing).
    header: (@Composable () -> Unit)? = null,
) {
    // With a header the popup must stay open on an empty (filtered-to-nothing)
    // list; without one, an empty list renders nothing as before.
    if (!expanded || (items.isEmpty() && header == null)) return
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
        Column(
            modifier = modifier
                .width(anchorWidthDp)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            // Header (e.g. the U21 search field) sits above the scrolling rows,
            // so it stays pinned while the list scrolls without needing the
            // experimental stickyHeader API.
            if (header != null) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    header()
                }
            }
            if (items.isEmpty()) {
                // Header present + nothing matched the query (guarded above).
                Text(
                    text = "No matches",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeightDp.dp),
                ) {
                    itemsIndexed(items = items, key = { _, item -> itemKey(item) }) { index, item ->
                        // Draw a divider before the first item flagged by
                        // dividerBefore (but never at the very top).
                        if (index > 0 && dividerBefore?.invoke(item) == true) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
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
