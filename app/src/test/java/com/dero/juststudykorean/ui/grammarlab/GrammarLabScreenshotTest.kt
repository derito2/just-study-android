package com.dero.juststudykorean.ui.grammarlab

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.grammar.LabPoint
import com.dero.juststudykorean.korean.grammar.Drill
import com.dero.juststudykorean.korean.grammar.Ending
import com.dero.juststudykorean.korean.grammar.LabVerb
import com.dero.juststudykorean.korean.grammar.WordKind
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** design/beta.md 3: Grammar Lab's three drills and a result, light and dark, at 411 dp (over a tab: no bottom bar). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class GrammarLabScreenshotTest {
    private val point = LabPoint("아서어서", "-아서/어서", "because, so", Ending.SoBecause, emptyList())
    private val base = GrammarLabUi(
        loading = false, points = listOf(point, point.copy(key = "으면", pattern = "-(으)면", meaning = "if", ending = Ending.If)),
        verbs = mapOf("먹다" to LabVerb("먹다", "To eat", WordKind.Verb)), level = 2,
    )
    private val choose = Drill.Choose("아서어서", "먹다", Ending.SoBecause, listOf("먹아서", "먹어서", "머서"), "먹어서")
    private val produce = Drill.Produce("아서어서", "먹다", Ending.SoBecause, setOf("먹어서"))
    private val fill = Drill.FillExample("아서어서", "먹다", Ending.SoBecause, "배가 고파서 밥을 ", " 기뻐요.", setOf("먹어서"), "I ate, so I'm happy.")

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$name.png") { JustStudyTheme(darkTheme = dark) { content() } }

    @Composable
    private fun Lab(ui: GrammarLabUi) = GrammarLabContent(ui, systemKeyboard = false, aiReady = false, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Test fun choose() = snap("grammarlab_choose", false) { Lab(base.copy(level = 1, drills = listOf(choose))) }
    @Test fun chooseDark() = snap("grammarlab_choose_dark", true) { Lab(base.copy(level = 1, drills = listOf(choose))) }
    @Test fun produce() = snap("grammarlab_produce", false) { Lab(base.copy(drills = listOf(produce), typed = "먹어")) }
    @Test fun produceDark() = snap("grammarlab_produce_dark", true) { Lab(base.copy(drills = listOf(produce), typed = "먹어")) }
    @Test fun fill() = snap("grammarlab_fill", false) { Lab(base.copy(level = 3, drills = listOf(fill))) }
    @Test fun fillDark() = snap("grammarlab_fill_dark", true) { Lab(base.copy(level = 3, drills = listOf(fill))) }

    private val wrong = base.copy(drills = listOf(produce), typed = "먹아서", result = LabResult("먹아서", false, "먹다 + -아서/어서 → 먹어서: 먹 has a final consonant and no ㅏ/ㅗ, so -어."))
    @Test fun result() = snap("grammarlab_result", false) { Lab(wrong) }
    @Test fun resultDark() = snap("grammarlab_result_dark", true) { Lab(wrong) }
}
