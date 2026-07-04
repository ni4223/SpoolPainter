package com.spoolpainter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * Dropdown menu with a **pinned** action row at the field-adjacent edge and a
 * scrolling item list underneath.
 *
 * Material 3's ExposedDropdownMenu scrolls its *entire* content, so a
 * high-value action (e.g. "Other", "Color Wheel") placed at one end can only be
 * always-visible (pinned top) OR field-adjacent (near end) — not both on a long
 * list (UI-46). This menu pins [pinnedContent] to the edge nearest the anchor
 * field and scrolls [items] in a LazyColumn between the pinned row and the far
 * edge, so the action never scrolls away regardless of list length.
 *
 * Open-direction is decided **once** here from the anchor bounds and reported to
 * the position provider, so the pinned edge and the popup placement can never
 * disagree:
 *  - opens **down** → `[ pinned action ] [ divider ] [ list ]` (action at top,
 *    adjacent to the field above it).
 *  - opens **up** → `[ list ] [ divider ] [ pinned action ]` (action at bottom,
 *    adjacent to the field below it).
 *
 * Anchored via [LazyDropdownAnchor] (shared with [LazyDropdownMenu]): the caller
 * spreads `anchor.modifier` across the OutlinedTextField so this component can
 * match its width and position off its global bounds.
 */
@Composable
fun <T : Any> PinnedActionMenu(
    expanded: Boolean,
    items: List<T>,
    anchor: LazyDropdownAnchor,
    onDismiss: () -> Unit,
    onItemClick: (T) -> Unit,
    itemKey: (T) -> Any,
    itemContent: @Composable (T) -> Unit,
    pinnedContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightDp: Int = 320,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val viewHeightPx = LocalView.current.height
    val anchorBounds = anchor.bounds ?: return
    val anchorWidthDp = with(density) { anchorBounds.width.toDp() }
    // Same rule ExposedDropdownMenu uses: flip up when there's less room below
    // the field than above it. Computed once so the Column order (below) and the
    // position provider agree on the field-adjacent edge.
    val opensUpward = (viewHeightPx - anchorBounds.bottom) < anchorBounds.top

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        popupPositionProvider = PinnedMenuPositionProvider(
            anchorBounds = anchorBounds,
            opensUpward = opensUpward,
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
            val list: @Composable () -> Unit = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Leave headroom for the pinned row: cap the scrolling
                        // list a little under the overall max so the pinned row
                        // never gets pushed off-screen on a full list.
                        .heightIn(max = (maxHeightDp - 56).coerceAtLeast(56).dp),
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
            if (opensUpward) {
                if (items.isNotEmpty()) {
                    list()
                    HorizontalDivider()
                }
                pinnedContent()
            } else {
                pinnedContent()
                if (items.isNotEmpty()) {
                    HorizontalDivider()
                    list()
                }
            }
        }
    }
}

/**
 * The "Other +" pinned action shared by [MaterialPicker] and [BrandPicker] —
 * an Add icon plus a primary-tinted SemiBold label that reveals an inline
 * custom-name field when tapped. Passed as the `pinnedContent` slot so it stays
 * field-adjacent regardless of open direction.
 */
@Composable
fun PinnedOtherAction(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private class PinnedMenuPositionProvider(
    private val anchorBounds: IntRect,
    private val opensUpward: Boolean,
    private val yOffsetPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds_: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val y = if (opensUpward) {
            // Place the popup so its bottom edge sits just above the field.
            (anchorBounds.top - yOffsetPx - popupContentSize.height).coerceAtLeast(0)
        } else {
            anchorBounds.bottom + yOffsetPx
        }
        return IntOffset(x = anchorBounds.left, y = y)
    }
}
