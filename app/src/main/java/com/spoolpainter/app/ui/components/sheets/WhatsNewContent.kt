package com.spoolpainter.app.ui.components.sheets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.graphics.vector.ImageVector

/** One row in the "What's new" showcase: leading icon, headline, body line. */
data class WhatsNewHighlight(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

/**
 * The versionCode in which [whatsNewV2Highlights] last changed.
 *
 * The sheet's trigger compares against THIS, not against the app's versionCode.
 * Keying it to the app version meant every release re-opened the sheet on the
 * same unchanged copy: the highlights below have been edited twice (created at
 * 106, camera row added at 110) across seven released versionCodes, so five of
 * those showings had nothing new in them. Repeatedly showing identical content
 * trains people to dismiss it unread, which costs us the one release where it
 * does matter.
 *
 * **Bump this to the current versionCode whenever, and only when, the copy below
 * changes.** It must never move backwards: existing installs store values from
 * the same versionCode space (106..112 in the wild), so a lower value would
 * silently suppress the sheet for people who have not seen the new copy.
 */
const val WHATS_NEW_CONTENT_VERSION: Int = 110

/**
 * Copy for the v2 showcase. Kept as data (not inline in the composable) so it's
 * unit-testable and a one-line edit per release. No em dashes in user-facing
 * strings per project convention.
 *
 * Editing this list means bumping [WHATS_NEW_CONTENT_VERSION].
 */
val whatsNewV2Highlights: List<WhatsNewHighlight> = listOf(
    WhatsNewHighlight(
        icon = Icons.Filled.Sync,
        title = "Your Spoolman companion",
        body = "Connected to your self hosted Spoolman server, SpoolPainter " +
            "helps keep your filament inventory and NFC tagged spools in " +
            "sync. Browse, create, and edit spools and filaments right from " +
            "the app. No account and no cloud, just local network sync.",
    ),
    WhatsNewHighlight(
        icon = Icons.Filled.Nfc,
        title = "Vendor tag support",
        body = "Tap a supported vendor tag and SpoolPainter will " +
            "automatically fill in the spool details for you. Bambu Lab, " +
            "Snapmaker, Creality, QIDI, Anycubic, and Elegoo tags are " +
            "supported.",
    ),
    WhatsNewHighlight(
        icon = Icons.Filled.Link,
        title = "Built for U1 firmware",
        body = "Use a tag's built in serial number to link it to a spool in " +
            "Spoolman. The latest Snapmaker U1 firmware can then identify the " +
            "correct spool automatically for seamless spool tracking, " +
            "including spools using their original vendor tags.",
    ),
    WhatsNewHighlight(
        icon = Icons.Filled.Nfc,
        title = "Pairing made easy",
        body = "Pair both tags for a spool without repeating the setup. Move " +
            "a tag to a different spool anytime and we'll automatically " +
            "update the pairing.",
    ),
    WhatsNewHighlight(
        icon = Icons.Filled.PhotoCamera,
        title = "Scan a color with the camera",
        body = "Point your camera at a spool and SpoolPainter samples the " +
            "color for you.",
    ),
    WhatsNewHighlight(
        icon = Icons.Filled.Palette,
        title = "Fresh new look",
        body = "SpoolPainter has been completely redesigned from the ground " +
            "up with a cleaner, more modern interface. Light and dark " +
            "themes, improved sorting, and more are built right in.",
    ),
)
