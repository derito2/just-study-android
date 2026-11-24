package com.dero.juststudykorean.ui.beta

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** design/beta.md 1: BETA and a Coming soon screen, light and dark, at 411 dp (over a tab: no bottom bar). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class BetaScreenshotTest {
    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$name.png") { JustStudyTheme(darkTheme = dark) { content() } }

    @Test
    fun beta() = snap("beta", dark = false) { BetaScreen(onBack = {}, onOpen = {}) }

    @Test
    fun betaDark() = snap("beta_dark", dark = true) { BetaScreen(onBack = {}, onOpen = {}) }

    @Test
    fun comingSoon() = snap("beta_coming_soon", dark = false) { ComingSoonScreen(BetaFeature.Listening, onBack = {}) }

    @Test
    fun comingSoonDark() = snap("beta_coming_soon_dark", dark = true) { ComingSoonScreen(BetaFeature.Listening, onBack = {}) }
}
