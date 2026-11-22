package com.dero.juststudykorean.ui.games

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Round 3 (OPPO): in the light theme Games' clock and icons were dark on the night scene. They are light while Games is shown. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class GamesStatusBarTest {
    @get:Rule(order = 0) val register = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()

    private fun lightIcons(): Boolean {
        val window = compose.activity.window
        return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
    }

    @Test
    fun theIconsAreLightWhileGamesIsShownAndComeBackAfterwards() {
        val window = compose.activity.window
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true // the light theme's default
        var shown by mutableStateOf(false)
        compose.setContent { if (shown) GamesStatusBarIcons() }
        compose.waitForIdle()
        assertTrue("before Games", lightIcons())
        shown = true
        compose.waitForIdle()
        assertFalse("while Games is on screen", lightIcons())
        shown = false
        compose.waitForIdle()
        assertTrue("after leaving Games", lightIcons())
    }
}
