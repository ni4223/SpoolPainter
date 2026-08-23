package com.spoolpainter.app.ui.activity

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.spoolpainter.app.data.local.SettingsRepository
import com.spoolpainter.app.data.local.ThemeOverride
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.hardware.nfc.NfcRepository
import com.spoolpainter.app.ui.components.sheets.WHATS_NEW_CONTENT_VERSION
import com.spoolpainter.app.ui.components.sheets.WhatsNewSheet
import com.spoolpainter.app.ui.components.sheets.whatsNewV2Highlights
import com.spoolpainter.app.ui.screens.main.MainScreen
import com.spoolpainter.app.ui.screens.settings.SettingsScreen
import com.spoolpainter.app.ui.theme.SpoolPainterTheme
import com.spoolpainter.app.ui.whatsnew.WhatsNewController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var nfcRepository: NfcRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var spoolmanRepository: SpoolmanRepository
    @Inject lateinit var whatsNewController: WhatsNewController

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        evaluateWhatsNew()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            val darkTheme = settings.themeOverride == ThemeOverride.Dark
            SpoolPainterTheme(darkTheme = darkTheme, dynamicColor = true) {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(onBack = { showSettings = false })
                } else {
                    MainScreen(onNavigateToSettings = { showSettings = true })
                }
                val showWhatsNew by whatsNewController.visible.collectAsStateWithLifecycle()
                WhatsNewSheet(
                    visible = showWhatsNew,
                    highlights = whatsNewV2Highlights,
                    onDismiss = { whatsNewController.onDismiss() },
                )
            }
        }
        intent?.let { tryDispatchNfcIntent(it) }
    }

    /**
     * Decide the one-time "What's new" showcase. A fresh install (first-install
     * time == last-update time) is suppressed; an in-place update is shown.
     * Evaluated once per cold start, before setContent observes the flow.
     */
    private fun evaluateWhatsNew() {
        val isFreshInstall = runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            info.firstInstallTime == info.lastUpdateTime
        }.getOrDefault(true)
        // Deliberately NOT BuildConfig.VERSION_CODE: the sheet should open when
        // the highlights change, not when the app version changes.
        whatsNewController.onColdStart(
            contentVersion = WHATS_NEW_CONTENT_VERSION,
            isFreshInstall = isFreshInstall,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tryDispatchNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        nfcRepository.attach(this)
        // F-6 (v2.0.3): refresh Spoolman cache when the user returns to the
        // app — catches the common case of editing in the Spoolman web UI in
        // a browser then switching back to SpoolPainter. Throttled internally
        // so a tight resume/pause cycle doesn't spam the server.
        lifecycleScope.launch {
            runCatching { spoolmanRepository.refreshIfStale() }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcRepository.detach()
    }

    private fun tryDispatchNfcIntent(intent: Intent) {
        when (intent.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED -> {
                val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
                }
                tag?.let { nfcRepository.onTagDiscovered(it) }
            }
        }
    }
}
