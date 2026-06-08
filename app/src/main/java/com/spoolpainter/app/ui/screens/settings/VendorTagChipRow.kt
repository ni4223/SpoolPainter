package com.spoolpainter.app.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.spoolpainter.app.hardware.nfc.vendor.VendorId

private val ReadyGreen = Color(0xFF2E7D32)
private val MissingRed = Color(0xFFC62828)

internal data class VendorRowSpec(
    val id: VendorId,
    val label: String,
    val ready: Boolean,
    val keyable: Boolean,
)

internal fun vendorRowSpecs(
    bambuSalt: String,
    crealitySalt: String,
    @Suppress("UNUSED_PARAMETER") crealityEncKey: String,
): List<VendorRowSpec> {
    val bambuReady = bambuSalt.isNotBlank()
    // Creality is "ready" once the HKDF salt is set; the AES enc key is
    // only required for encrypted tags, plaintext ones work with just the
    // salt. Per plan §1.3.
    val crealityReady = crealitySalt.isNotBlank()
    return listOf(
        VendorRowSpec(VendorId.Anycubic, "Anycubic", ready = true, keyable = false),
        VendorRowSpec(VendorId.Bambu, "Bambu Lab", ready = bambuReady, keyable = true),
        VendorRowSpec(VendorId.Creality, "Creality", ready = crealityReady, keyable = true),
        VendorRowSpec(VendorId.Elegoo, "Elegoo", ready = true, keyable = false),
        VendorRowSpec(VendorId.OpenSpool, "OpenSpool", ready = true, keyable = false),
        VendorRowSpec(VendorId.Qidi, "QIDI", ready = true, keyable = false),
        VendorRowSpec(VendorId.Snapmaker, "Snapmaker", ready = true, keyable = false),
    )
}

/**
 * Three-column "table" layout for the vendor list. Custom [Layout] measures
 * every cell once, then sets each column's width to the widest cell in that
 * column across ALL rows. So the status-glyph and key-button columns line
 * up vertically without any pixel guessing.
 *
 * Column 0 (brand name) gets the widest brand width.
 * Column 1 (status glyph) gets a fixed 24 dp.
 * Column 2 (key button slot) gets a fixed 44 dp (room for the drop shadow).
 *
 * Cells carry `layoutId(row * 3 + col)` so the layout pass can group them.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun VendorTagSupportList(
    bambuSalt: String,
    crealitySalt: String,
    crealityEncKey: String,
    selected: VendorId?,
    onKeyTap: (VendorId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = vendorRowSpecs(bambuSalt, crealitySalt, crealityEncKey)
    val rowCount = rows.size

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings-vendor-list"),
        content = {
            rows.forEachIndexed { rowIdx, row ->
                // Cell (rowIdx, 0) — brand name.
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .layoutId(rowIdx * COL_COUNT + 0)
                        .testTag("settings-vendor-row-${row.id.name.lowercase()}"),
                )
                // Cell (rowIdx, 1) — status glyph.
                Box(
                    modifier = Modifier
                        .layoutId(rowIdx * COL_COUNT + 1)
                        .size(STATUS_W),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = if (row.ready) "Working" else "Missing key",
                        tint = if (row.ready) ReadyGreen else MissingRed,
                        modifier = Modifier.size(20.dp),
                    )
                }
                // Cell (rowIdx, 2) — key button slot (empty for non-keyable rows).
                Box(
                    modifier = Modifier
                        .layoutId(rowIdx * COL_COUNT + 2)
                        .size(KEY_W),
                    contentAlignment = Alignment.Center,
                ) {
                    if (row.keyable) {
                        KeyButton(
                            row = row,
                            isSelected = row.id == selected,
                            onClick = { onKeyTap(row.id) },
                        )
                    }
                }
            }
        },
    ) { measurables, constraints ->
        // First pass: measure every cell with no minimum constraints so we
        // see each one's intrinsic size.
        val cellConstraints = Constraints(maxWidth = constraints.maxWidth)
        val placeables = Array(rowCount * COL_COUNT) { idx ->
            val m: Measurable = measurables.first { it.layoutId == idx }
            m.measure(cellConstraints)
        }

        // Second pass: column widths = max measured width per column.
        val colWidths = IntArray(COL_COUNT) { col ->
            (0 until rowCount).maxOf { rowIdx -> placeables[rowIdx * COL_COUNT + col].width }
        }
        val colSpacingPx = COL_SPACING.roundToPx()
        val rowSpacingPx = ROW_SPACING.roundToPx()
        val rowVerticalPaddingPx = ROW_V_PADDING.roundToPx()

        // The table fills the available width. Layout columns left-to-right:
        //   col 0 (brand) hugs the left
        //   col 1 (status glyph) sits next to the brand, separated by a
        //     small spacing so they read as a pair
        //   col 2 (key button) is pushed to the right edge of the row
        // Total horizontal "ink" is colWidths.sum() + 1 spacing (brand↔glyph);
        // the gap before col 2 stretches to fill the rest of the row.
        val tableWidth = constraints.maxWidth
        val brandGlyphInkWidth = colWidths[0] + colSpacingPx + colWidths[1]
        val keyColX = tableWidth - colWidths[2]

        val rowHeights = IntArray(rowCount) { rowIdx ->
            (0 until COL_COUNT).maxOf { col -> placeables[rowIdx * COL_COUNT + col].height } +
                rowVerticalPaddingPx * 2
        }
        val totalHeight = rowHeights.sum() + rowSpacingPx * (rowCount - 1).coerceAtLeast(0)

        layout(width = tableWidth, height = totalHeight) {
            var y = 0
            for (rowIdx in 0 until rowCount) {
                val rowHeight = rowHeights[rowIdx]
                val brandP = placeables[rowIdx * COL_COUNT + 0]
                val statusP = placeables[rowIdx * COL_COUNT + 1]
                val keyP = placeables[rowIdx * COL_COUNT + 2]

                // Vertical center each cell within the row.
                val brandY = y + (rowHeight - brandP.height) / 2
                val statusY = y + (rowHeight - statusP.height) / 2
                val keyY = y + (rowHeight - keyP.height) / 2

                // Brand at left edge.
                brandP.placeRelative(0, brandY)
                // Status glyph sits centered in the gap between the brand
                // column and the key column — neither glued to the brand
                // text nor all the way at the right edge.
                val brandEnd = colWidths[0]
                val statusSlotX = (brandEnd + keyColX) / 2 - colWidths[1] / 2
                statusP.placeRelative(statusSlotX + (colWidths[1] - statusP.width) / 2, statusY)
                // Key button slot pinned to the right edge.
                keyP.placeRelative(keyColX + (colWidths[2] - keyP.width) / 2, keyY)

                y += rowHeight + (if (rowIdx < rowCount - 1) rowSpacingPx else 0)
            }
            // Reference brandGlyphInkWidth so the compiler doesn't warn it's unused;
            // it's a useful local for readers to see the layout intent above.
            @Suppress("UNUSED_VARIABLE") val ignored = brandGlyphInkWidth
        }
    }
}

private const val COL_COUNT = 3
private val STATUS_W = 24.dp
private val KEY_W = 44.dp
private val COL_SPACING = 12.dp
private val ROW_SPACING = 2.dp
private val ROW_V_PADDING = 4.dp

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun KeyButton(row: VendorRowSpec, isSelected: Boolean, onClick: () -> Unit) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val iconTint = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = iconTint,
        tonalElevation = if (isSelected) 6.dp else 2.dp,
        shadowElevation = if (isSelected) 8.dp else 4.dp,
        modifier = Modifier
            .size(36.dp)
            .testTag("settings-vendor-row-${row.id.name.lowercase()}-keybtn"),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Outlined.VpnKey,
                contentDescription = "Enter ${row.label} key",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
