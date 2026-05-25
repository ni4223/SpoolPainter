package com.spoolpainter.app.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.ui.common.UiEffect

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is UiEffect.Navigate -> if (effect.destination == "settings") onNavigateToSettings()
            }
        }
    }

    Scaffold(
        topBar = { MainTopBar(onSettingsClick = viewModel::onSettingsTapped) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ReadFab(
                isReading = state.activeFlow == ActiveFlow.ReadingForPair,
                onClick = viewModel::onReadTapped,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("main-screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BannerSlot(state.banner)
            ReadingHint(state.activeFlow, state.nfc)
            UidRow(state.form.cardUid?.hex)
            SpoolmanDropdown(
                spools = state.spoolman.spools,
                selectedId = state.spoolman.selectedSpoolId,
                enabled = state.spoolman.urlConfigured,
                onSelect = viewModel::onSpoolSelected,
            )
            AmbiguityBlock(state.ambiguity)
            FormPreview(state.form)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(onSettingsClick: () -> Unit) {
    TopAppBar(
        title = { Text("SpoolPainter") },
        actions = {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.testTag("main-settings-button"),
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
    )
}

@Composable
private fun BannerSlot(banner: BannerState) {
    when (banner) {
        BannerState.Hidden -> Unit
        is BannerState.Offline -> {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("main-banner-offline"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(
                    text = "Spoolman unreachable" + (banner.lastError?.let { " — $it" } ?: ""),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ReadingHint(activeFlow: ActiveFlow, nfc: NfcResult) {
    val showHint = activeFlow == ActiveFlow.ReadingForPair &&
        (nfc is NfcResult.Idle || nfc is NfcResult.Reading)
    if (!showHint) return
    Text(
        text = "Tap a tag to read…",
        modifier = Modifier.fillMaxWidth().testTag("main-reading-hint"),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun UidRow(uidHex: String?) {
    if (uidHex.isNullOrEmpty()) return
    Text(
        text = "UID: ${uidHex.uppercase()}",
        modifier = Modifier.fillMaxWidth().testTag("main-uid-row"),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpoolmanDropdown(
    spools: List<SpoolmanSpool>,
    selectedId: Int?,
    enabled: Boolean,
    onSelect: (SpoolmanSpool?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = spools.firstOrNull { it.id == selectedId }
    val displayText = if (!enabled) {
        "Configure Spoolman URL in Settings"
    } else {
        selected?.let { spoolDisplayName(it) } ?: "Select a Spoolman spool…"
    }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth().testTag("main-spoolman-dropdown"),
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Spool") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        if (enabled) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Clear selection") },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
                spools.forEach { spool ->
                    DropdownMenuItem(
                        text = { Text(spoolDisplayName(spool)) },
                        onClick = {
                            onSelect(spool)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

internal fun spoolDisplayName(spool: SpoolmanSpool): String {
    val parts = listOfNotNull(
        spool.filament.name?.takeIf { it.isNotBlank() },
        spool.filament.vendor?.name?.takeIf { it.isNotBlank() },
        "#${spool.id ?: "?"}",
    )
    return parts.joinToString(" · ")
}

@Composable
private fun AmbiguityBlock(state: AmbiguityState?) {
    if (state == null) return
    Card(
        modifier = Modifier.fillMaxWidth().testTag("main-ambiguity-block"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Multiple spools claim UID ${state.uid.hex.uppercase()}",
                style = MaterialTheme.typography.titleSmall,
            )
            state.matches.forEach { spool ->
                Text(
                    text = "• #${spool.id ?: "?"} · ${spool.filament.name ?: "—"} · ${spool.filament.vendor?.name ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "Pick one from the dropdown to resolve, or fix the data in Spoolman.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FormPreview(form: FormState) {
    Card(modifier = Modifier.fillMaxWidth().testTag("main-form-preview")) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewRow("Material", form.material?.name)
            PreviewRow("Brand", form.brand?.name)
            ColorPreviewRow(form.colorHex)
            PreviewRow("Variant", form.variant)
            HorizontalDivider()
            PreviewRow(
                "Extruder",
                tempRangeText(form.tempRanges.extruderMin, form.tempRanges.extruderMax),
            )
            PreviewRow(
                "Bed",
                tempRangeText(form.tempRanges.bedMin, form.tempRanges.bedMax),
            )
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ColorPreviewRow(colorHex: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Colour",
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (colorHex != null) {
            val color = parseHex(colorHex)
            if (color != null) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(text = "#$colorHex", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(text = "—", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun parseHex(hex: String): Color? {
    if (hex.length != 6) return null
    return try {
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        Color(red = r, green = g, blue = b)
    } catch (_: NumberFormatException) {
        null
    }
}

private fun tempRangeText(min: Int?, max: Int?): String {
    return when {
        min == null && max == null -> "—"
        else -> "${min ?: "—"}–${max ?: "—"} °C"
    }
}

@Composable
private fun ReadFab(isReading: Boolean, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = Modifier.testTag("main-read-fab"),
    ) {
        Text(if (isReading) "Reading…" else "Read tag")
    }
}
