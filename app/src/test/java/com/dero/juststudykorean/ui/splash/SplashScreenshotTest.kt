package com.dero.juststudykorean.ui.splash

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The launch picture with its title in place (moment 0.6), at 411 dp. Only a flash picture: the others are not in the repo. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class SplashScreenshotTest {
    @Test
    fun splash() {
        assertEquals(3, FlashArts.size)
        captureRoboImage("src/test/screenshots/splash.png") {
            JustStudyTheme(darkTheme = false) { SplashContent(FlashArts[0]) { 0.6f } }
        }
    }
}
