package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.dero.juststudykorean.ui.theme.Tokens
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** design/polish.md 8: the common components in all their variants, light and dark, at 411 dp. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class CommonComponentsScreenshotTest {
    private fun snap(name: String, content: @Composable () -> Unit) {
        for (dark in listOf(false, true)) {
            captureRoboImage("src/test/screenshots/${name}_${if (dark) "dark" else "light"}.png") {
                CompositionLocalProvider(LocalFrozenMotion provides true) {
                    JustStudyTheme(darkTheme = dark) {
                        Column(Modifier.width(Tokens.ScreenW).background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
                    }
                }
            }
        }
    }

    @Test
    fun resultBanners() = snap("resultbanner") {
        ResultBanner(BannerTone.Correct, "Correct!", ko = "정답!") { Text("Your answer: 도서관", color = com.dero.juststudykorean.ui.theme.JustStudyTheme.colors.fillInk) }
        ResultBanner(BannerTone.Wrong, "Not quite") {
            Text("Your answer: to be tall", color = JustStudyTheme.colors.fillMuted)
            Text("Correct answer: to be short", color = JustStudyTheme.colors.fillInk)
        }
        ResultBanner(BannerTone.Neutral, "Compare it with your notes") { Text("The order of your notes is shown below.", color = JustStudyTheme.colors.fillInk) }
        ResultBanner(BannerTone.Warn, "Not enough space on the phone.", icon = Lucide.BadgeAlert, action = { StickerButton("Try again", {}, color = StickerColor.Gray, size = StickerSize.Small) }) {
            Text("Free up some space and try again.", color = JustStudyTheme.colors.fillInk)
        }
    }

    @Test
    fun pills() = snap("pill") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Queued")
            Pill("Processing", PillTone.Info)
            Pill("Ready", PillTone.Correct, icon = Lucide.Check)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Failed", PillTone.Wrong)
            Pill("Not supported", PillTone.Warn)
            Pill("213 cards")
        }
    }
}
