package com.spoolpainter.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SpoolSortKey
import com.spoolpainter.app.data.local.TagStorageMode
import com.spoolpainter.app.domain.models.TagSyncState
import com.spoolpainter.app.ui.common.UiEffect
import com.spoolpainter.app.ui.components.ThemeToggleSwitch

// General feedback channel — kept in production (UI-37 closed 2026-07-25).
private const val FEEDBACK_URL = "https://forms.gle/Yx94vLHCSaBWRL1m9"

// Vendor-tag-specific report form. Pre-filled via the diagnostic entry ID
// when the user taps "Report a tag issue" from Settings.
private const val TAG_REPORT_FORM_BASE =
    "https://docs.google.com/forms/d/e/1FAIpQLSfRfHF4sOlyjGB6WXJDc_gt70CIByXKnxQMViIF7YJl3MCY2g/viewform"
private const val TAG_REPORT_DIAGNOSTIC_ENTRY = "entry.85549585"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val themeOverride by viewModel.themeOverride.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            if (effect is UiEffect.ShowSnackbar) {
                snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    var draftUrl by rememberSaveable(state.url) { mutableStateOf(state.url) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings-back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ThemeToggleSwitch(
                        current = themeOverride,
                        onToggle = viewModel::onThemeToggled,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.imePadding(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("settings-screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draftUrl,
                onValueChange = { draftUrl = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-url-field"),
                label = { Text("Spoolman URL") },
                placeholder = { Text("http://192.168.1.100:7912") },
                textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            )
            Button(
                onClick = { viewModel.onUrlSaved(draftUrl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-save"),
            ) {
                Text("Save")
            }
            OutlinedButton(
                onClick = viewModel::onRefreshTapped,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-refresh"),
            ) {
                Text("Refresh spool list")
            }
            SettingsTagStorageSection(
                selected = state.tagStorageMode,
                serverVersion = state.serverVersion,
                supportsNativeTags = state.supportsNativeTags,
                tagSyncState = state.tagSyncState,
                onSelect = viewModel::onTagStorageModeSelected,
                testTag = "settings-tag-storage",
            )
            SettingsSortSection(
                label = "Spool list sort",
                selectedKey = state.spoolSortKey,
                direction = state.spoolSortDirection,
                keys = SpoolSortKey.values(),
                keyLabel = ::spoolSortKeyLabel,
                onKeySelected = viewModel::onSpoolSortKeyChanged,
                onDirectionChanged = viewModel::onSpoolSortDirectionChanged,
                testTag = "settings-spool-sort",
            )
            SettingsSortSection(
                label = "Filament list sort",
                selectedKey = state.filamentSortKey,
                direction = state.filamentSortDirection,
                keys = FilamentSortKey.values(),
                keyLabel = ::filamentSortKeyLabel,
                onKeySelected = viewModel::onFilamentSortKeyChanged,
                onDirectionChanged = viewModel::onFilamentSortDirectionChanged,
                testTag = "settings-filament-sort",
            )
            SettingsCurrencySection(
                selected = state.currency,
                onSelect = viewModel::onCurrencyChanged,
                testTag = "settings-currency",
            )
            SettingsVendorSection(
                bambuSalt = state.bambuSalt,
                crealitySalt = state.crealitySalt,
                crealityEncKey = state.crealityEncKey,
                onBambuSaltSaved = viewModel::onBambuSaltSaved,
                onCrealitySaltSaved = viewModel::onCrealitySaltSaved,
                onCrealityEncKeySaved = viewModel::onCrealityEncKeySaved,
                testTag = "settings-vendor",
            )
            SettingsSupportSection(testTag = "settings-support")
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(FEEDBACK_URL))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.testTag("settings-feedback"),
                ) {
                    Text("Send feedback")
                }
            }
            // Tester quick-feedback: copy the last NFC scan diagnostic to the
            // clipboard and open the feedback form. Disabled until the user
            // has tapped at least one tag this session.
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                androidx.compose.material3.TextButton(
                    onClick = {
                        // Build the diagnostic block, URL-encode it into the
                        // pre-fill param, and launch the form. No clipboard
                        // step — the diagnostic field is filled before the
                        // user even sees the form.
                        val diagnostic = viewModel.buildNfcShareText(formUrl = null)
                        val encoded = java.net.URLEncoder.encode(diagnostic, "UTF-8")
                        val url = "$TAG_REPORT_FORM_BASE?usp=pp_url" +
                            "&$TAG_REPORT_DIAGNOSTIC_ENTRY=$encoded"
                        val open = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(open)
                    },
                    enabled = viewModel.hasNfcReads(),
                    modifier = Modifier.testTag("settings-share-nfc"),
                ) {
                    Text("Report a tag issue")
                }
            }
        }
    }
}

/**
 * Issue #19 Phase 3 — NFC tag storage mode picker. Three radio rows
 * (pattern of [com.spoolpainter.app.ui.components.WeightMethodRadio]'s
 * RadioOption) pick where written tag UIDs land: native tags + legacy
 * card_uids (default, Snapmaker U1 compatible), native only, or legacy
 * only. A capability status line above the options reflects the Spoolman
 * server info observed by the ViewModel; when the server predates 0.27
 * the repository silently falls back to legacy writes, so nothing is
 * disabled — the copy just stays informative.
 */
@Composable
private fun SettingsTagStorageSection(
    selected: TagStorageMode,
    serverVersion: String?,
    supportsNativeTags: Boolean,
    tagSyncState: TagSyncState,
    onSelect: (TagStorageMode) -> Unit,
    testTag: String,
) {
    // Capability status from the observed serverInfo: version + gate when
    // probed, neutral hint when the server hasn't been checked yet.
    val (capabilityText, capabilityColor) = when {
        serverVersion == null -> "Test the connection to check this server's native tag support." to
            MaterialTheme.colorScheme.onSurfaceVariant
        supportsNativeTags -> "Spoolman $serverVersion — native tags available" to
            MaterialTheme.colorScheme.onSurfaceVariant
        else -> "Spoolman $serverVersion (< 0.27) — native tags unavailable, legacy is used" to
            MaterialTheme.colorScheme.error
    }
    // Passive mirror status — only once a run has actually completed.
    val syncText = tagSyncSummaryLine(tagSyncState)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "NFC tag storage", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = capabilityText,
            style = MaterialTheme.typography.bodySmall,
            color = capabilityColor,
            modifier = Modifier.testTag("$testTag-capability"),
        )
        if (syncText != null) {
            Text(
                text = syncText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("$testTag-sync"),
            )
        }
        TagStorageOptionRow(
            title = "Native + legacy (default)",
            description = "Writes NFC tag UIDs to Spoolman's native tag system and the legacy " +
                "card_uids field. Compatible with Snapmaker U1 firmware.",
            warning = null,
            selected = selected == TagStorageMode.BOTH,
            onClick = { if (selected != TagStorageMode.BOTH) onSelect(TagStorageMode.BOTH) },
            testTag = "$testTag-both",
        )
        TagStorageOptionRow(
            title = "Native only",
            description = "Uses Spoolman's native tags only; no card_uids extra field is created.",
            warning = "Snapmaker U1 printers with default firmware read card_uids — they will " +
                "not see tags written by SpoolPainter in this mode.",
            selected = selected == TagStorageMode.NATIVE_ONLY,
            onClick = { if (selected != TagStorageMode.NATIVE_ONLY) onSelect(TagStorageMode.NATIVE_ONLY) },
            testTag = "$testTag-native_only",
        )
        TagStorageOptionRow(
            title = "Legacy only",
            description = "Writes only the legacy card_uids field (current behavior).",
            warning = null,
            selected = selected == TagStorageMode.LEGACY_ONLY,
            onClick = { if (selected != TagStorageMode.LEGACY_ONLY) onSelect(TagStorageMode.LEGACY_ONLY) },
            testTag = "$testTag-legacy_only",
        )
    }
}

/** "Auto-mirror: 12 tags synced" style line for the section; null while
 *  no mirror run has completed yet. Conflicts and failures append as
 *  comma-separated counts. */
private fun tagSyncSummaryLine(state: TagSyncState): String? {
    val finished = state as? TagSyncState.Finished ?: return null
    val clean = finished.conflicts.isEmpty() && finished.failed == 0
    val parts = mutableListOf<String>()
    if (finished.mirrored > 0 || clean) {
        parts += if (clean) "${finished.mirrored} tags synced" else "${finished.mirrored} synced"
    }
    if (finished.conflicts.isNotEmpty()) {
        parts += "${finished.conflicts.size} conflict" +
            if (finished.conflicts.size == 1) " (skipped)" else "s (skipped)"
    }
    if (finished.failed > 0) parts += "${finished.failed} failed"
    return "Auto-mirror: " + parts.joinToString(", ")
}

/** One selectable radio row: RadioButton + title, description and an
 *  optional warning line. Selected rows tint primaryContainer, matching
 *  the app's existing radio styling. */
@Composable
private fun TagStorageOptionRow(
    title: String,
    description: String,
    warning: String?,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .testTag(testTag),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (warning != null) {
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
