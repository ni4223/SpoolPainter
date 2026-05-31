package com.spoolpainter.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spoolpainter.app.data.local.Currency
import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SpoolSortKey
import com.spoolpainter.app.ui.common.UiEffect
import com.spoolpainter.app.ui.components.ThemeToggleSwitch

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
            SettingsSegmentedSection(
                label = "Currency",
                options = listOf(
                    Currency.Dollar to "$ Dollar",
                    Currency.Euro to "€ Euro",
                    Currency.Generic to "¤ Money",
                ),
                selected = state.currency,
                onSelect = viewModel::onCurrencyChanged,
                testTag = "settings-currency",
            )
        }
    }
}
