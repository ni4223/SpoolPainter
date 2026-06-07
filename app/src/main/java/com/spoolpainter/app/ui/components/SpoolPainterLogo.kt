package com.spoolpainter.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.spoolpainter.app.R
import androidx.compose.ui.unit.dp

@Composable
fun SpoolPainterLogo(
    color: Color,
    modifier: Modifier = Modifier,
    showText: Boolean = true,
    /**
     * When non-null, renders a halo behind the tinted logo (same vector
     * scaled up + tinted with [outlineColor]) so low-contrast colours
     * (black on dark, white on light) stay visible without flipping the
     * tint itself. Tuned with scale 1.10 for a thick visible halo.
     */
    outlineColor: Color? = null,
) {
    val colorFilter = ColorFilter.tint(color, BlendMode.Modulate)
    val outlineFilter = outlineColor?.let { ColorFilter.tint(it, BlendMode.Modulate) }

    if (!showText) {
        LogoImageWithOptionalHalo(
            modifier = modifier,
            sizeModifier = Modifier.fillMaxSize(),
            colorFilter = colorFilter,
            outlineFilter = outlineFilter,
        )
        return
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 2026-06-06: visual anchor is the spool HOLE at screen vertical
        // center (user request). The vector viewport is 600×341 with NFC
        // waves at x≈460–600 (~23% of width), so the spool body's center
        // sits left of the bounding-box center. We add a leading Spacer
        // sized to mirror the NFC-waves area (40dp at 96dp height ≈ image
        // width × 23%) so the parent Column's CenterHorizontally shifts
        // the image rightward — net effect: spool hole on screen center,
        // NFC waves visibly to the right. Title row sits naturally
        // centered beneath the now-shifted spool. Row + Spacer is the
        // right layout primitive here — no Modifier.offset
        // [[feedback_no_offset_modifier]].
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(40.dp))
            LogoImageWithOptionalHalo(
                modifier = Modifier,
                sizeModifier = Modifier.height(96.dp),
                colorFilter = colorFilter,
                outlineFilter = outlineFilter,
            )
        }
        Text(
            text = "Spool Painter",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun LogoImageWithOptionalHalo(
    modifier: Modifier,
    sizeModifier: Modifier,
    colorFilter: ColorFilter,
    outlineFilter: ColorFilter?,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (outlineFilter != null) {
            Image(
                painter = painterResource(id = R.drawable.spool_logo),
                contentDescription = null,
                modifier = sizeModifier.scale(scaleX = 1.04f, scaleY = 1.10f),
                colorFilter = outlineFilter,
                contentScale = ContentScale.Fit,
            )
        }
        Image(
            painter = painterResource(id = R.drawable.spool_logo),
            contentDescription = "SpoolPainter",
            modifier = sizeModifier,
            colorFilter = colorFilter,
            contentScale = ContentScale.Fit,
        )
    }
}
