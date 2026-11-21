package com.dero.juststudykorean.ui.splash

import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** design/splash.md §6: the public scenes are read without error and a broken SVG never throws; captures at both OPPO densities (2.625 and 3.07). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class SplashScreenshotTest {
    private val publicScenes = File("src/main/assets/splash").listFiles { f -> f.name.startsWith("flash") && f.name.endsWith(".svg") }!!.sortedBy { it.name }

    @Test
    fun thePublicScenesAreRead() {
        assertTrue(publicScenes.isNotEmpty())
        for (file in publicScenes) {
            val scene = readScene(file.inputStream())
            assertNotNull(file.name, scene)
            assertTrue(file.name, scene!!.layers.size in 1..16)
            assertTrue(file.name, scene.layers.sumOf { it.paths.size } <= 700)
            assertTrue(file.name, file.length() <= 250_000)
        }
    }

    @Test
    fun aBrokenSceneReturnsNullInsteadOfThrowing() {
        for (bad in listOf("", "<svg", "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 10'/>", "<svg viewBox='0 0 1080 2400'><g id='a'><path fill='#000000'/></g></svg>", "<svg viewBox='0 0 1080 2400'><g id='a'><path d='M 1 1 L 5")) {
            assertNull(bad, readScene(bad.byteInputStream()))
        }
    }

    private fun snap(name: String) {
        val scene = readScene(publicScenes.first().inputStream())
        assertEquals(true, scene != null)
        captureRoboImage("src/test/screenshots/$name.png") {
            CompositionLocalProvider(LocalFrozenMotion provides true) {
                JustStudyTheme(darkTheme = false) { SplashContent(scene, still = true) { 0.6f } }
            }
        }
    }

    @Test
    fun splash() = snap("splash")

    @Test
    @Config(qualifiers = "w411dp-h905dp-491dpi")
    fun splashDense() = snap("splash_3_07")
}
