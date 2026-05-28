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
import com.spoolpainter.app.hardware.nfc.NfcRepository
import com.spoolpainter.app.ui.screens.main.MainScreen
import com.spoolpainter.app.ui.screens.settings.SettingsScreen
import com.spoolpainter.app.ui.theme.SpoolPainterTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var nfcRepository: NfcRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpoolPainterTheme {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(onBack = { showSettings = false })
                } else {
                    MainScreen(onNavigateToSettings = { showSettings = true })
                }
            }
        }
        intent?.let { tryDispatchNfcIntent(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tryDispatchNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        nfcRepository.attach(this)
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
