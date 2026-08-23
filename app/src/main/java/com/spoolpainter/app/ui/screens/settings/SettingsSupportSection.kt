package com.spoolpainter.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.spoolpainter.app.R
import kotlinx.coroutines.delay

/**
 * A shop this project links out to.
 *
 * [code] is a checkout coupon code the vendor issues, set only where one exists;
 * null means the link is the only mechanism. [accent] is the vendor's brand
 * colour, used for the button outline. [label] is required even when artwork is
 * shown, because it is the button's accessibility name.
 */
internal data class ReferralTarget(
    val label: String,
    val url: String,
    val code: String? = null,
    val accent: Color,
)

// Brand colours match the README's shields.io badges.
private val PolymakerTeal = Color(0xFF108474)
private val SnapmakerBlue = Color(0xFF00B2E3)

// Mirrors the README's Support section. Polymaker is a link only (no code);
// Snapmaker issues coupon code ni42 alongside its three regional storefronts.
//
// The myshopify.com hosts are the entry points Snapmaker issues, and each 301s
// to the canonical store while preserving ?ref: us -> us.snapmaker.com,
// eu -> eu.snapmaker.com, and test-snapmaker -> shop.snapmaker.com. That last
// one reads like a staging host but is the real global store (verified
// 2026-08-22); do not "fix" it to a prettier domain, the issued entry point is
// what gets attributed.
internal val POLYMAKER_TARGET = ReferralTarget(
    label = "Polymaker",
    url = "https://shop.polymaker.com/NI42",
    accent = PolymakerTeal,
)

internal val SNAPMAKER_TARGETS: List<ReferralTarget> = listOf(
    ReferralTarget("US", "https://snapmaker-us.myshopify.com?ref=ni42", "ni42", SnapmakerBlue),
    ReferralTarget("EU", "https://snapmaker-eu.myshopify.com?ref=ni42", "ni42", SnapmakerBlue),
    ReferralTarget("Global", "https://test-snapmaker.myshopify.com?ref=ni42", "ni42", SnapmakerBlue),
)

internal val REFERRAL_TARGETS: List<ReferralTarget> =
    listOf(POLYMAKER_TARGET) + SNAPMAKER_TARGETS

/** How long the copy button holds its confirmed state before reverting. */
private const val COPIED_FEEDBACK_MS = 2_000L

/**
 * "Support the project" card at the bottom of Settings.
 *
 * Deliberately a Card rather than another settings row: it is not a setting, and
 * blending it into the list made it read as one. Always visible (no expander) so
 * it is findable without a tap, and placed below every real setting so it never
 * sits between the user and the tag workflow. Not surfaced on the main screen or
 * in the What's new sheet.
 *
 * Layout: Polymaker takes a full-width row; the three Snapmaker regions share one
 * row as equal-weight buttons, which is compact and overflow-proof on a narrow
 * screen. Brand colour is applied to the outline, not the label, because neither
 * accent carries enough contrast for text on a light background.
 */
@Composable
internal fun SettingsSupportSection(
    testTag: String,
    polymaker: ReferralTarget = POLYMAKER_TARGET,
    snapmaker: List<ReferralTarget> = SNAPMAKER_TARGETS,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Support the project",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            ShopButton(
                target = polymaker,
                testTag = "$testTag-polymaker",
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Polymaker's mark is brand-teal by design, so it is drawn as-is
                // rather than tinted: it reads on both the light and dark theme.
                Image(
                    painter = painterResource(R.drawable.ic_polymaker_wordmark),
                    contentDescription = polymaker.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(22.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Snapmaker ship their wordmark as monochrome black/white
                // variants, so tinting to the theme foreground is faithful to the
                // brand AND legible either way, from one asset.
                Image(
                    painter = painterResource(R.drawable.ic_snapmaker_wordmark),
                    contentDescription = "Snapmaker",
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.height(15.dp),
                )
                snapmaker.forEach { target ->
                    ShopButton(
                        target = target,
                        testTag = "$testTag-snapmaker-${target.label.lowercase()}",
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = target.label,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
            snapmaker.firstNotNullOfOrNull { it.code }?.let { code ->
                CouponCodeRow(code = code, testTag = "$testTag-coupon")
            }
        }
    }
}

/**
 * The coupon code, as a button that copies it. A code you have to select by hand
 * off a screen is a code you mistype at checkout, so copying is the primary
 * action rather than an afterthought.
 *
 * Android 13+ shows its own copy confirmation, but minSdk here is 29, so the
 * button carries its own: it swaps to a checkmark for [COPIED_FEEDBACK_MS]. That
 * is also the only feedback on 13+ that appears next to the thing you tapped.
 */
@Composable
private fun CouponCodeRow(code: String, testTag: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Snapmaker coupon",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(code))
                copied = true
            },
            modifier = Modifier.testTag(testTag),
        ) {
            Text(
                text = if (copied) "Copied" else code,
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(
                imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                contentDescription = if (copied) "Copied" else "Copy coupon code",
                modifier = Modifier
                    .padding(start = 6.dp)
                    .height(16.dp),
            )
        }
    }
}

@Composable
private fun ShopButton(
    target: ReferralTarget,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target.url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        },
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = SolidColor(target.accent),
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
        modifier = modifier.testTag(testTag),
    ) {
        content()
    }
}
