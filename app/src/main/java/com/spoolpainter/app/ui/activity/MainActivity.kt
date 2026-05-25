package com.spoolpainter.app.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.spoolpainter.app.ui.screens.main.MainScreen
import com.spoolpainter.app.ui.theme.SpoolPainterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpoolPainterTheme {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // TODO U4: nfcRepository.attach(this)
    }

    override fun onPause() {
        super.onPause()
        // TODO U4: nfcRepository.detach()
    }
}
