package com.dero.juststudykorean

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.dero.juststudykorean.data.prefs.ThemeMode
import com.dero.juststudykorean.data.prefs.UserPrefs
import com.dero.juststudykorean.ui.navigation.AppScaffold
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var prefs: UserPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // null until DataStore answers: the window background shows meanwhile (no flash of the wrong theme).
            val mode by prefs.themeMode.collectAsState(initial = null)
            val dark = when (mode ?: return@setContent) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            val scope = rememberCoroutineScope()
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
                AppScaffold(
                    darkMode = dark,
                    onDarkMode = { on -> scope.launch { prefs.setThemeMode(if (on) ThemeMode.Dark else ThemeMode.Light) } },
                )
            }
        }
    }
}
