package com.spoolpainter.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spoolpainter.app.data.local.ThemeOverride

/**
 * Light↔Dark theme toggle. ON = Dark, OFF = Light. Sun/moon thumb icon
 * makes the current state legible without colour cues; tap flips it.
 */
@Composable
fun ThemeToggleSwitch(
    current: ThemeOverride,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = current == ThemeOverride.Dark
    Switch(
        checked = isDark,
        onCheckedChange = { onToggle() },
        modifier = modifier.testTag("theme-toggle"),
        thumbContent = {
            Icon(
                imageVector = if (isDark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                contentDescription = if (isDark) {
                    "Theme: Dark (tap to switch to Light)"
                } else {
                    "Theme: Light (tap to switch to Dark)"
                },
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
    )
}
