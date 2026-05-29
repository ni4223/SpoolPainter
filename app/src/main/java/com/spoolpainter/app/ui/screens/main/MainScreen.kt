package com.spoolpainter.app.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.ui.common.UiEffect
import com.spoolpainter.app.ui.components.FilamentForm
import com.spoolpainter.app.ui.components.FormChange
import com.spoolpainter.app.ui.components.sheets.BottomSheetHost
import com.spoolpainter.app.ui.components.sheets.PairAnotherTagUiState
import com.spoolpainter.app.ui.components.sheets.RepairConfirmViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    repairConfirmViewModel: RepairConfirmViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val customMaterial by viewModel.customMaterial.collectAsStateWithLifecycle()
    val customBrand by viewModel.customBrand.collectAsStateWithLifecycle()
    val canWrite by viewModel.canWrite.collectAsStateWithLifecycle()
    val filaments by viewModel.filaments.collectAsStateWithLifecycle()
    val materials by viewModel.materials.collectAsStateWithLifecycle()
    val brands by viewModel.brands.collectAsStateWithLifecycle()
    val repairConfirmState by repairConfirmViewModel.uiState.collectAsStateWithLifecycle()
    val pairAnotherState by remember(state.activeFlow) {
        derivedStateOf {
            (state.activeFlow as? ActiveFlow.PromptingPairAnother)?.let {
                PairAnotherTagUiState(spoolId = it.spoolId, visible = true)
            }
        }
    }
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
            BottomSheetHost(
                activeFlow = state.activeFlow,
                repairConfirmState = repairConfirmState,
                pairAnotherState = pairAnotherState,
                onRepairConfirm = repairConfirmViewModel::onConfirm,
                onRepairDismiss = repairConfirmViewModel::onDismiss,
                onPairAnotherAccept = viewModel::onPairAnotherTagAccepted,
                onPairAnotherDismiss = viewModel::onPairAnotherTagDismissed,
            )
            ReadingHint(state.activeFlow, state.nfc)
            WritingHint(state.activeFlow, state.nfc)
            if (state.spoolman.urlConfigured) {
                SpoolmanDropdown(
                    spools = state.spoolman.spools,
                    selectedId = state.spoolman.selectedSpoolId,
                    enabled = state.activeFlow == ActiveFlow.Idle && state.spoolman.reachable,
                    onSelect = viewModel::onSpoolSelected,
                )
            }
            VendorTagHint(
                observed = state.observedTagKind,
                hasUid = state.observedTagUid != null,
                urlConfigured = state.spoolman.urlConfigured,
                alreadyLinked = state.spoolman.selectedSpoolId != null,
            )
            AmbiguityBlock(state.ambiguity)
            FilamentForm(
                state = state.form,
                customMaterial = customMaterial,
                customBrand = customBrand,
                enabled = state.activeFlow == ActiveFlow.Idle,
                canSave = canWrite,
                filaments = filaments,
                materials = materials,
                brands = brands,
                onChange = { change ->
                    when (change) {
                        is FormChange.MaterialPicked -> viewModel.onMaterialPicked(change.value)
                        is FormChange.CustomMaterialChanged -> viewModel.onCustomMaterialChanged(change.value)
                        is FormChange.BrandPicked -> viewModel.onBrandPicked(change.value)
                        is FormChange.CustomBrandChanged -> viewModel.onCustomBrandChanged(change.value)
                        is FormChange.ColorHex -> viewModel.onColorHexChanged(change.value)
                        is FormChange.Variant -> viewModel.onVariantChanged(change.value)
                        is FormChange.TempRangesChanged -> viewModel.onTempRangesChanged(change.value)
                        is FormChange.FilamentSelected -> viewModel.onFilamentSelected(change.value)
                        is FormChange.FilamentSectionToggled -> viewModel.onFilamentSectionToggled()
                        is FormChange.MoreDetailsToggled -> viewModel.onMoreDetailsToggled()
                        is FormChange.EmptySpoolWeightChanged -> viewModel.onEmptySpoolWeightChanged(change.value)
                        is FormChange.PriceChanged -> viewModel.onPriceChanged(change.value)
                        is FormChange.FullSpoolWeightChanged -> viewModel.onFullSpoolWeightChanged(change.value)
                        is FormChange.DiameterChanged -> viewModel.onDiameterChanged(change.value)
                        is FormChange.DensityChanged -> viewModel.onDensityChanged(change.value)
                    }
                },
                onSave = viewModel::onWriteTapped,
                saveButtonLabel = when {
                    state.observedTagKind == ObservedTagKind.Vendor &&
                        state.writeMode == WriteMode.Spoolman -> "Save & Map"
                    state.writeMode == WriteMode.RawNoUrl -> "Write to NFC"
                    else -> "Save & Write"
                },
            )
            WritingHint(state.activeFlow, state.nfc)
            InstructionFooter(state.activeFlow)
        }
    }
}

/**
 * v1-style instructional footer at the bottom of the screen. Shown only when
 * idle (no read/write in flight) so it doesn't compete with the in-flight
 * hints at the top.
 */
@Composable
private fun InstructionFooter(activeFlow: ActiveFlow) {
    if (activeFlow != ActiveFlow.Idle) return
    Text(
        text = "• Tap a tag to read its filament settings\n" +
            "• Or fill the form, then tap Save & Write to write a fresh tag\n" +
            "• Press Read tag to scan a tag without filling the form first",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("main-instructions"),
        style = MaterialTheme.typography.bodySmall,
    )
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
                    text = "Spoolman unreachable" + (banner.lastError?.let { ": $it" } ?: ""),
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
private fun WritingHint(activeFlow: ActiveFlow, nfc: NfcResult) {
    val label = when {
        activeFlow is ActiveFlow.WritingSecondTag &&
            (nfc is NfcResult.Idle || nfc is NfcResult.Writing) ->
            "Tap second tag to write…"
        activeFlow == ActiveFlow.WritingForPair &&
            (nfc is NfcResult.Idle || nfc is NfcResult.Writing) ->
            "Tap a tag to write…"
        (activeFlow == ActiveFlow.WritingForPair ||
            activeFlow is ActiveFlow.WritingSecondTag) &&
            nfc is NfcResult.Verifying -> "Verifying tag…"
        activeFlow == ActiveFlow.PairingVendorUidOnly -> "Linking tag to spool…"
        else -> return
    }
    Text(
        text = label,
        modifier = Modifier.fillMaxWidth().testTag("main-writing-hint"),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
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
    // Cached spools include archived entries (move-on-bind needs them) but
    // the dropdown is for picking an active spool to write to. Sort newest
    // first — recently-created spools are the most likely target after a
    // pair, so users shouldn't have to scroll past everything to find them.
    val visibleSpools = spools
        .filterNot { it.archived }
        .sortedByDescending { it.id ?: Int.MIN_VALUE }
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
            trailingIcon = {
                if (selected != null && enabled) {
                    IconButton(
                        onClick = {
                            expanded = false
                            onSelect(null)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("main-spoolman-dropdown-clear"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear spool selection",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            shape = RoundedCornerShape(20.dp),
        )
        if (enabled) {
            // ExposedDropdownMenu (not bare DropdownMenu) auto-scrolls past
            // its viewport — bare DropdownMenu silently clips long lists, so
            // recently-created spools (later IDs) drop off the bottom and
            // appear "missing" even when present in state.
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.clip(RoundedCornerShape(20.dp)),
            ) {
                visibleSpools.forEach { spool ->
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
private fun ReadFab(isReading: Boolean, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = Modifier.testTag("main-read-fab"),
    ) {
        Text(if (isReading) "Reading…" else "Read tag")
    }
}

/**
 * Card rendered when the most recent tag observed is vendor-classified
 * (factory-encoded — we can't read its contents, but we can still link its
 * UID to a Spoolman spool per FR-4.9). Save & Write on this state routes
 * through [VendorUidOnlyPairUseCase] (no NDEF write).
 */
@Composable
private fun VendorTagHint(
    observed: ObservedTagKind,
    hasUid: Boolean,
    urlConfigured: Boolean,
    alreadyLinked: Boolean,
) {
    if (observed != ObservedTagKind.Vendor || !hasUid) return
    Card(
        modifier = Modifier.fillMaxWidth().testTag("main-vendor-tag-hint"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Vendor tag",
                style = MaterialTheme.typography.titleSmall,
            )
            val body = when {
                alreadyLinked -> null
                urlConfigured -> "Content can't be read. Pick a spool from the dropdown or fill the form, then tap Save & Write to link this tag."
                else -> "Content can't be read. Configure Spoolman in Settings to link this tag to a spool."
            }
            if (body != null) {
                Text(text = body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
