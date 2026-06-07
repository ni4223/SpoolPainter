package com.spoolpainter.app.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.data.local.SpoolSortKey
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.ui.common.UiEffect
import com.spoolpainter.app.ui.components.FilamentForm
import com.spoolpainter.app.ui.components.FormChange
import com.spoolpainter.app.ui.components.MoreDetailsExpander
import com.spoolpainter.app.ui.components.SaveToSpoolmanButton
import com.spoolpainter.app.ui.components.SpoolPainterLogo
import com.spoolpainter.app.ui.components.sheets.BottomSheetHost
import com.spoolpainter.app.ui.components.sheets.PairAnotherTagUiState
import com.spoolpainter.app.ui.components.sheets.RepairConfirmViewModel
import com.spoolpainter.app.ui.components.spoolComparator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    repairConfirmViewModel: RepairConfirmViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val customMaterial by viewModel.customMaterial.collectAsStateWithLifecycle()
    val customBrand by viewModel.customBrand.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()
    val canWrite by viewModel.canWrite.collectAsStateWithLifecycle()
    val isReadInFlight by viewModel.isReadInFlight.collectAsStateWithLifecycle()
    val isWriteCancellable by viewModel.isWriteCancellable.collectAsStateWithLifecycle()
    val filaments by viewModel.filaments.collectAsStateWithLifecycle()
    val materials by viewModel.materials.collectAsStateWithLifecycle()
    val brands by viewModel.brands.collectAsStateWithLifecycle()
    val isSpoolmanRefreshing by viewModel.isSpoolmanRefreshing.collectAsStateWithLifecycle()
    val repairConfirmState by repairConfirmViewModel.uiState.collectAsStateWithLifecycle()
    val pairAnotherState by remember(state.activeFlow) {
        derivedStateOf {
            (state.activeFlow as? ActiveFlow.PromptingPairAnother)?.let {
                PairAnotherTagUiState(
                    spoolId = it.spoolId,
                    visible = true,
                    isVendorPair = it.isVendorPair,
                )
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

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val snackbarLift = (configuration.screenHeightDp * 0.25f).dp
    Scaffold(
        snackbarHost = {
            // Lifted into the lower-middle of the screen (about 18% above
            // the bottom inset) so important transient messages don't hug
            // the gesture bar. Custom Snackbar with bodyLarge text +
            // generous padding for legibility.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(bottom = snackbarLift, start = 16.dp, end = 16.dp),
                snackbar = { data ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = data.visuals.message,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        // Box stacks: page content underneath, centered NFC status overlay
        // on top while a tag-waiting flow is running.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        // F-6 (v2.0.3): pull-to-refresh wrapping the entire scroll content.
        // Standard Material 3 PullToRefreshBox; gesture lives at the top of
        // the page (consistent with Gmail/Twitter). Triggers a force-refresh
        // (bypasses the throttle since this is explicitly user-initiated).
        // No-op when Spoolman URL is not configured — viewModel's handler
        // returns early on UrlNotConfigured outcome and the spinner just
        // dismisses without doing real work.
        PullToRefreshBox(
            isRefreshing = isSpoolmanRefreshing,
            onRefresh = viewModel::onPullToRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("main-screen"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MainLogoHeader(
                colorHex = state.form.colorHex,
                onSettingsClick = viewModel::onSettingsTapped,
            )
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
            val parsedVendor = (state.nfc as? com.spoolpainter.app.domain.primitives.NfcResult.Success)
                ?.classification
                ?.let { it as? com.spoolpainter.app.domain.primitives.TagClassification.Vendor }
                ?.parsedHint != null
            VendorTagHint(
                observed = state.observedTagKind,
                hasUid = state.observedTagUid != null,
                urlConfigured = state.spoolman.urlConfigured,
                alreadyLinked = state.spoolman.selectedSpoolId != null,
                showChip = state.nfc is com.spoolpainter.app.domain.primitives.NfcResult.Success ||
                    state.spoolman.selectedSpoolId != null,
                parsed = parsedVendor,
            )
            AmbiguityBlock(state.ambiguity)

            // U13 §1.3 — single outer Card wrapping the three editable
            // sections (Spoolman dropdown / FilamentForm / MoreDetailsExpander)
            // plus the Save button. Inner sections render as elevation-0 Cards
            // with a thin surfaceVariant border (Q-U13-3=B) — preserves
            // visual section separation while signalling "all in one
            // container that saves together."
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("main-outer-card"),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.spoolman.urlConfigured) {
                        InnerSectionCard(testTag = "main-spoolman-card") {
                            SpoolmanDropdown(
                                spools = state.spoolman.spools,
                                sortKey = state.spoolSortKey,
                                sortDirection = state.spoolSortDirection,
                                selectedId = state.spoolman.selectedSpoolId,
                                enabled = state.activeFlow == ActiveFlow.Idle && state.spoolman.reachable,
                                onSelect = viewModel::onSpoolSelected,
                            )
                        }
                    }
                    InnerSectionCard(testTag = "main-form-card") {
                        FilamentForm(
                            state = state.form,
                            customMaterial = customMaterial,
                            customBrand = customBrand,
                            enabled = state.activeFlow == ActiveFlow.Idle,
                            identityLocked = state.form.selectedSpoolId != null ||
                                state.form.selectedFilamentId != null,
                            spoolmanConfigured = state.spoolman.urlConfigured,
                            spoolmanReachable = state.spoolman.reachable,
                            filaments = filaments,
                            materials = materials,
                            brands = brands,
                            filamentSortKey = state.filamentSortKey,
                            filamentSortDirection = state.filamentSortDirection,
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
                                    is FormChange.MoreDetailsToggled -> viewModel.onMoreDetailsToggled()
                                    is FormChange.EmptySpoolWeightChanged -> viewModel.onEmptySpoolWeightChanged(change.value)
                                    is FormChange.PriceChanged -> viewModel.onPriceChanged(change.value)
                                    is FormChange.FullSpoolWeightChanged -> viewModel.onFullSpoolWeightChanged(change.value)
                                    is FormChange.DensityChanged -> viewModel.onDensityChanged(change.value)
                                    is FormChange.WeightMethodPicked -> viewModel.onWeightMethodPicked(change.value)
                                    is FormChange.ActiveWeightChanged -> viewModel.onActiveWeightChanged(change.value)
                                }
                            },
                        )
                    }
                    val activeWeightValueG = when (state.form.weightMethod) {
                        WeightMethod.Remaining -> state.form.remainingWeightG
                        WeightMethod.Measured -> state.form.measuredEntry
                            ?: state.form.remainingWeightG?.let { rem ->
                                state.form.emptySpoolWeightG?.let { empty -> rem + empty }
                            }
                    }
                    InnerSectionCard(testTag = "main-more-details-card") {
                        MoreDetailsExpander(
                            expanded = state.form.moreDetailsExpanded,
                            enabled = state.activeFlow == ActiveFlow.Idle,
                            spoolmanConfigured = state.spoolman.urlConfigured,
                            spoolmanReachable = state.spoolman.reachable,
                            // v2.1 — filament-spec edits unlocked on existing-spool path
                            // (Save PATCHes filament via sparseDiff). Filament-picker
                            // path keeps the lock: a new sibling spool of an existing
                            // filament inherits its parent's spec (decision K).
                            filamentSpecLocked = state.form.selectedSpoolId == null &&
                                state.form.selectedFilamentId != null,
                            showSpoolScopeFields = state.form.selectedSpoolId != null,
                            tempRanges = state.form.tempRanges,
                            emptySpoolWeightG = state.form.emptySpoolWeightG,
                            priceMajor = state.form.priceMajor,
                            priceSuffix = state.priceSuffix,
                            fullSpoolWeightG = state.form.fullSpoolWeightG,
                            densityGPerCm3 = state.form.densityGPerCm3,
                            weightMethod = state.form.weightMethod,
                            activeWeightValueG = activeWeightValueG,
                            onWeightMethodPicked = viewModel::onWeightMethodPicked,
                            onActiveWeightChange = viewModel::onActiveWeightChanged,
                            onToggle = viewModel::onMoreDetailsToggled,
                            onTempRangesChange = viewModel::onTempRangesChanged,
                            onEmptySpoolWeightChange = viewModel::onEmptySpoolWeightChanged,
                            onPriceChange = viewModel::onPriceChanged,
                            onFullSpoolWeightChange = viewModel::onFullSpoolWeightChanged,
                            onDensityChange = viewModel::onDensityChanged,
                        )
                    }
                    // Save to Spoolman lives at the bottom of the outer Card —
                    // signals "everything in here saves together". Hidden in
                    // RawNoUrl mode (no Spoolman target).
                    if (state.writeMode == WriteMode.Spoolman) {
                        SaveToSpoolmanButton(
                            enabled = canSave,
                            onClick = viewModel::onSaveTapped,
                            label = if (canSave) {
                                computeSaveLabel(
                                    selectedSpoolId = state.spoolman.selectedSpoolId,
                                    selectedFilamentId = state.form.selectedFilamentId,
                                )
                            } else {
                                "Save to Spoolman"
                            },
                        )
                    }
                    // Read/Write demoted to inline text buttons under Save.
                    // Save is the primary commit; tag I/O is a secondary
                    // step. Each button toggles to "Cancel" while its own
                    // tag-waiting flow is in flight.
                    InlineReadWriteRow(
                        isReadInFlight = isReadInFlight,
                        isWriteCancellable = isWriteCancellable,
                        canRead = state.activeFlow == ActiveFlow.Idle ||
                            state.activeFlow == ActiveFlow.ReadingForPair,
                        canWrite = canWrite,
                        writeMode = state.writeMode,
                        observedTagKind = state.observedTagKind,
                        writeHint = computeWriteHint(
                            activeFlow = state.activeFlow,
                            selectedSpoolId = state.spoolman.selectedSpoolId,
                            selectedFilamentId = state.form.selectedFilamentId,
                            observed = state.observedTagKind,
                            writeMode = state.writeMode,
                        ),
                        onRead = viewModel::onReadTapped,
                        onWrite = viewModel::onWriteTapped,
                    )
                }
            }
        }
        }
        // Centered NFC status overlay. Pinned to the middle of the screen
        // while a tag-waiting flow is in flight. Big, easy to read, doesn't
        // move with scroll. Pointer-passthrough so the form underneath
        // remains scrollable; tapping the overlay does nothing — Cancel
        // lives in the inline row.
        NfcStatusOverlay(
            label = computeStatusLabel(state.activeFlow, state.nfc),
        )
        }
    }
}

/**
 * U13 §1.3 — inner section helper. Tonal Surface in `surfaceContainerHigh`
 * (Q-U13-3 revised: 2026-06-06 picked M3 tonal-palette container token over
 * the dated `surfaceVariant`). Sits inside the outer Card to give each
 * section a subtle color block without competing with the outer
 * elevation-5 lift.
 */
@Composable
private fun InnerSectionCard(testTag: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            content()
        }
    }
}

/**
 * Inline Read/Write row. Sits inside the outer Card just under
 * SaveToSpoolmanButton (Save is now primary; tag I/O is secondary —
 * 2026-06-06 reframe). When ANY tag-waiting flow is in flight (Read,
 * standard NDEF Write, or pair-another second tag), the two buttons
 * collapse into a single full-width Cancel. Non-Save HTTP-only flows
 * (vendor UID-only pair) leave the row in its idle shape with both
 * buttons disabled — no Cancel surface, ~250ms HTTP completes itself.
 */
@Composable
private fun InlineReadWriteRow(
    isReadInFlight: Boolean,
    isWriteCancellable: Boolean,
    canRead: Boolean,
    canWrite: Boolean,
    writeMode: WriteMode,
    observedTagKind: ObservedTagKind,
    writeHint: String?,
    onRead: () -> Unit,
    onWrite: () -> Unit,
) {
    val inFlight = isReadInFlight || isWriteCancellable
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("main-inline-actions"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (inFlight) {
            // Shared Cancel — single full-width button. Whichever flow is
            // running owns the cancel; routes to the right VM handler.
            // Status text lives on the centered NfcStatusOverlay (above the
            // page), not here — the Cancel button stays minimal.
            androidx.compose.material3.OutlinedButton(
                onClick = if (isReadInFlight) onRead else onWrite,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("main-inline-cancel"),
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onRead,
                    enabled = canRead,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("main-inline-read"),
                ) {
                    Text(
                        text = "Read tag",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                val writeLabel = when {
                    writeMode == WriteMode.Spoolman && observedTagKind == ObservedTagKind.Vendor -> "Map tag"
                    writeMode == WriteMode.RawNoUrl -> "Write to NFC"
                    else -> "Write tag"
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = onWrite,
                    enabled = canWrite,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("main-inline-write"),
                ) {
                    Text(
                        text = writeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            // Supporting text under the Write half only — visually attached
            // to the disabled button it's about. Uses an empty Spacer in
            // the Read half (weight=1f) so the hint sits in the right half
            // matching the button width above it.
            writeHint?.let { hint ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("main-inline-write-hint"),
                    )
                }
            }
        }
    }
}

/**
 * State-aware hint rendered as supporting text under the Write button while
 * Write is disabled. Pairs with the Save button's current label so the
 * user reads label + hint as a two-step instruction.
 *
 *  - Spool selected               → no hint (Write is enabled).
 *  - Vendor tag, no spool         → "Create a spool to map this tag."
 *  - Filament selected, no spool  → "Create spool first."
 *  - Nothing selected             → "Create filament and spool first."
 *
 * Returns null in non-blocking states so no caption renders.
 */
private fun computeWriteHint(
    activeFlow: ActiveFlow,
    selectedSpoolId: Int?,
    selectedFilamentId: Int?,
    observed: ObservedTagKind,
    writeMode: WriteMode,
): String? {
    if (activeFlow != ActiveFlow.Idle) return null
    if (writeMode == WriteMode.RawNoUrl) return null
    if (selectedSpoolId != null) return null
    if (observed == ObservedTagKind.Vendor) return "Create a spool to map this tag."
    if (selectedFilamentId != null) return "Create spool first."
    return "Create filament and spool first."
}

/**
 * State-aware Save button label. Save is form-HTTP only (no UID work).
 * Vendor UID mapping moved off Save and onto Write 2026-06-06.
 *
 *  - Spool selected:    "Update" (PATCH form edits).
 *  - Filament selected: "Create spool" (POST spool against existing filament).
 *  - Nothing selected:  "Create filament and spool" (POST both).
 */
private fun computeSaveLabel(
    selectedSpoolId: Int?,
    selectedFilamentId: Int?,
): String = when {
    selectedSpoolId != null -> "Update"
    selectedFilamentId != null -> "Create spool"
    else -> "Create filament and spool"
}

@Composable
private fun MainLogoHeader(colorHex: String?, onSettingsClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    val tint = remember(colorHex, outline) { parseLogoColor(colorHex) ?: outline }
    // When the picked filament colour's luminance is too close to the
    // surface (near-black on dark theme, near-white on light theme), the
    // tinted logo silhouette would vanish. Render a silver halo behind
    // it so the spool shape stays visible while the literal filament
    // colour is preserved on the front face.
    val haloColor = remember(tint, surface) {
        val delta = kotlin.math.abs(tint.luminance() - surface.luminance())
        if (delta < 0.2f) androidx.compose.ui.graphics.Color(0xFFC0C0C0) else null
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        SpoolPainterLogo(
            color = tint,
            outlineColor = haloColor,
            modifier = Modifier.fillMaxWidth(),
        )
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopEnd)
                .testTag("main-settings-button"),
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun parseLogoColor(hex: String?): androidx.compose.ui.graphics.Color? {
    if (hex.isNullOrBlank() || hex.length != 6) return null
    return try {
        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor("#$hex"))
    } catch (_: IllegalArgumentException) {
        null
    }
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
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Spoolman unreachable",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    banner.lastError?.takeIf { it.isNotBlank() }?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Centered NFC status overlay. Pinned to the middle of the screen while a
 * tag-waiting flow is running. Big, easy to read, doesn't move with
 * scroll. Pointer-passthrough on the surrounding Box so the form
 * underneath stays scrollable; Cancel lives in the inline row inside the
 * outer Card, not on this overlay.
 */
@Composable
private fun BoxScope.NfcStatusOverlay(label: String?) {
    androidx.compose.animation.AnimatedVisibility(
        visible = label != null,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.85f),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.85f),
        modifier = Modifier.align(Alignment.Center),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.testTag("main-status-overlay"),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RadiatingWavesIndicator(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(72.dp),
                )
                Text(
                    text = label ?: "",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Three concentric circles pulsing outward from a center dot — the universal
 * "tap your phone here" sign. Each ring is phase-offset by 1/3 of the cycle
 * so a new ring starts as the previous fades. Fully Compose-side; no
 * drawables needed.
 */
@Composable
private fun RadiatingWavesIndicator(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "nfc-waves")
    val cycleMs = 1800
    val phases = listOf(0f, 1f / 3f, 2f / 3f)
    val animations = phases.map { phase ->
        transition.animateFloat(
            initialValue = phase,
            targetValue = phase + 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = cycleMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "nfc-wave-$phase",
        )
    }
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = size.minDimension / 2f
        drawCircle(
            color = color,
            radius = maxRadius * 0.12f,
            center = Offset(cx, cy),
        )
        animations.forEach { animState ->
            val t = animState.value % 1f
            val radius = maxRadius * (0.18f + t * 0.82f)
            val alpha = (1f - t).coerceIn(0f, 1f)
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = maxRadius * 0.06f),
            )
        }
    }
}

/**
 * Status caption text used by [NfcStatusOverlay] while a tag-waiting flow
 * is running. Replaces the old top-of-screen pill that pushed content
 * down on flow start and was off-screen if the user had scrolled.
 */
private fun computeStatusLabel(activeFlow: ActiveFlow, nfc: NfcResult): String? = when {
    activeFlow == ActiveFlow.ReadingForPair &&
        (nfc is NfcResult.Idle || nfc is NfcResult.Reading) ->
        "Tap a tag to read"
    activeFlow is ActiveFlow.WritingSecondTag &&
        (nfc is NfcResult.Idle || nfc is NfcResult.Writing) ->
        // Pair-another runs through TwoTagUseCase which tries NDEF first;
        // if the second tap is a vendor tag, it re-routes to the
        // HTTP-only vendor pair. Either way the user just needs to tap a
        // tag, so the label drops the "to write" half (which lied for
        // the vendor branch).
        "Tap second tag to pair"
    activeFlow == ActiveFlow.WritingForPair &&
        (nfc is NfcResult.Idle || nfc is NfcResult.Writing) ->
        "Tap a tag to write"
    (activeFlow == ActiveFlow.WritingForPair ||
        activeFlow is ActiveFlow.WritingSecondTag) &&
        nfc is NfcResult.Verifying -> "Verifying tag"
    activeFlow == ActiveFlow.PairingVendorUidOnly -> "Linking tag to spool"
    else -> null
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpoolmanDropdown(
    spools: List<SpoolmanSpool>,
    sortKey: SpoolSortKey,
    sortDirection: SortDirection,
    selectedId: Int?,
    enabled: Boolean,
    onSelect: (SpoolmanSpool?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val anchor = com.spoolpainter.app.ui.components.rememberLazyDropdownAnchor()
    // ExposedDropdownMenu renders every row eagerly (no lazy column), so
    // every recomposition that re-runs filter+sort+row-string-builder
    // multiplies by N spools. Cache the sorted list + per-row display
    // tuple so a tap-toggle isn't paying for the work again.
    val visibleSpools = remember(spools, sortKey, sortDirection) {
        spools.filterNot { it.archived }
            .sortedWith(spoolComparator(sortKey, sortDirection))
    }
    val visibleRows = remember(visibleSpools) {
        visibleSpools.map { spool ->
            SpoolRowDisplay(
                spool = spool,
                primary = spoolPrimaryRow(spool),
                secondary = spoolSecondaryRow(spool),
                colorHex = spool.filament.color_hex,
            )
        }
    }
    val selected = spools.firstOrNull { it.id == selectedId }
    val displayText = if (!enabled) {
        "Configure Spoolman URL in Settings"
    } else {
        selected?.let { spoolSelectedDisplay(it) } ?: "Spools in Spoolman"
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
            modifier = Modifier.fillMaxWidth().menuAnchor().then(anchor.modifier),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            shape = RoundedCornerShape(20.dp),
        )
        if (enabled) {
            // LazyDropdownMenu (custom) instead of ExposedDropdownMenu —
            // the latter composes every row eagerly. With 50+ spools and
            // a multi-composable PickerRow per item, that's a half-second
            // tap-to-open lag. Lazy compose drops first-open work to the
            // visible row count.
            com.spoolpainter.app.ui.components.LazyDropdownMenu(
                expanded = expanded,
                items = visibleRows,
                anchor = anchor,
                onDismiss = { expanded = false },
                onItemClick = { row ->
                    onSelect(row.spool)
                    expanded = false
                },
                itemKey = { row -> row.spool.id ?: row.primary.hashCode() },
                itemContent = { row ->
                    com.spoolpainter.app.ui.components.PickerRow(
                        primary = row.primary,
                        secondary = row.secondary,
                        colorHex = row.colorHex,
                    )
                },
            )
        }
    }
}

/** Cached row display tuple — built once per visibleSpools change. */
@androidx.compose.runtime.Immutable
private data class SpoolRowDisplay(
    val spool: SpoolmanSpool,
    val primary: String,
    val secondary: String,
    val colorHex: String?,
)

/**
 * Compact text for the Spool picker after selection. User direction:
 * just name + spool id — material/brand/colour flow into the form so
 * re-stating them in the picker is noise.
 */
internal fun spoolSelectedDisplay(spool: SpoolmanSpool): String {
    val filamentName = spool.filament.name?.takeIf { it.isNotBlank() }
        ?: spool.filament.material ?: "Unknown"
    return "$filamentName · #${spool.id ?: "?"}"
}

/** Bold first line of the open-dropdown row: 'Vendor · Name'. */
internal fun spoolPrimaryRow(spool: SpoolmanSpool): String {
    val vendorName = spool.filament.vendor?.name?.takeIf { it.isNotBlank() }
    val filamentName = spool.filament.name?.takeIf { it.isNotBlank() }
        ?: spool.filament.material ?: "Unknown"
    return if (vendorName != null) "$vendorName · $filamentName" else filamentName
}

/** Faded second line: 'Material · Variant · #id' (variant only when set). */
internal fun spoolSecondaryRow(spool: SpoolmanSpool): String {
    val variant = spool.filament.extra?.get("variant")
        ?.let { raw ->
            if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
                raw.substring(1, raw.length - 1)
            } else raw
        }?.takeIf { it.isNotBlank() }
    val parts = listOfNotNull(
        spool.filament.material?.takeIf { it.isNotBlank() },
        variant,
        "#${spool.id ?: "?"}",
    )
    return parts.joinToString(" · ")
}

/** Kept for legacy callers (snackbar/log strings) — verbose format. */
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

/**
 * Inline info row rendered when the most recent tag observed is
 * vendor-classified (factory-encoded — we can't read its contents, but we
 * can still link it to a Spoolman spool per FR-4.9). Save & Write on this
 * state routes through [VendorUidOnlyPairUseCase] (no NDEF write).
 *
 * Visually distinct from form Cards: outlined, tertiary-tinted icon + bold
 * label + supporting body line. Sits on the screen background, not inside
 * a Card.
 */
@Composable
private fun VendorTagHint(
    observed: ObservedTagKind,
    hasUid: Boolean,
    urlConfigured: Boolean,
    alreadyLinked: Boolean,
    showChip: Boolean,
    parsed: Boolean,
) {
    if (!showChip || observed != ObservedTagKind.Vendor || !hasUid) return
    val body = when {
        alreadyLinked -> "We can't read this tag's contents. Tap Save to pair it with the selected spool."
        urlConfigured -> "We can't read this tag's contents. Pick a spool or fill the form, then tap Save to pair it."
        else -> "We can't read this tag's contents. Configure Spoolman in Settings to pair this tag with a spool."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("main-vendor-tag-hint")
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Vendor tag",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
            )
            if (!parsed) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
