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
        LogoImageWithOptionalHalo(
            modifier = Modifier,
            sizeModifier = Modifier.height(96.dp),
            colorFilter = colorFilter,
            outlineFilter = outlineFilter,
        )
        // Title centred on the spool body, not on the spool+NFC-waves
        // bounding box. The vector viewport is 600×341 with NFC waves at
        // x≈460–600 (~23% of width); we pair the Text with a trailing
        // Spacer of that proportional width so the parent Column's
        // CenterHorizontally centring shifts the text leftward to align
        // with the spool. Using a Row + Spacer keeps the layout proper —
        // no Modifier.offset (which doesn't compose with the parent and
        // breaks across screen sizes / densities).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Spool Painter",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.width(20.dp))
        }
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
