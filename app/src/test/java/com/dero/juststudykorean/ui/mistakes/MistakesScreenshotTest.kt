package com.dero.juststudykorean.ui.mistakes

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.korean.mistakes.Mistake
import com.dero.juststudykorean.korean.mistakes.patterns
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** design/beta.md 6: My Mistakes with patterns and empty, light and dark, at 411 dp. Synthetic mistakes only. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class MistakesScreenshotTest {
    private val list = patterns(listOf(
        Mistake("Grammar Lab", "먹어서", "먹아서", "먹다", "아서어서", 5),
        Mistake("Quiz", "좋아요", "좋어요", null, null, 4),
        Mistake("Quiz", "거울", "구름", "거울", null, 3),
        Mistake("Flashcards", "학교", null, "학교", null, 2),
        Mistake("Quiz", "책을 읽어요", "책를 읽어요", "책", null, 1),
    ), since = 0)
    private val full = MistakesUi(loading = false, period = 1, patterns = list)

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$name.png") { JustStudyTheme(darkTheme = dark) { content() } }

    @Composable
    private fun Screen(ui: MistakesUi) = MistakesContent(ui, {}, {}, {}, {})

    @Test fun patterns() = snap("mistakes_patterns", false) { Screen(full) }
    @Test fun patternsDark() = snap("mistakes_patterns_dark", true) { Screen(full) }
    @Test fun empty() = snap("mistakes_empty", false) { Screen(MistakesUi(loading = false)) }
    @Test fun emptyDark() = snap("mistakes_empty_dark", true) { Screen(MistakesUi(loading = false)) }
}
