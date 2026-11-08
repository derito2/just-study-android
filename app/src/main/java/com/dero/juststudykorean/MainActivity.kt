package com.dero.juststudykorean

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dero.juststudykorean.ui.navigation.AppScaffold
import com.dero.juststudykorean.ui.theme.JustStudyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // shortcut: dark mode choice in memory only; task 4 stores it in DataStore.
            var choice by rememberSaveable { mutableStateOf<Boolean?>(null) }
            val dark = choice ?: isSystemInDarkTheme()
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    // Status bar icons follow the app theme, not only the system one.
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    // The bottom bar (`action`) goes under the system buttons: white buttons, no contrast scrim.
                    navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                )
                window.isNavigationBarContrastEnforced = false
                onDispose {}
            }
            JustStudyTheme(darkTheme = dark) {
                AppScaffold(darkMode = dark, onDarkMode = { choice = it })
            }
        }
    }
}
