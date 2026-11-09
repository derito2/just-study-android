package com.dero.juststudykorean.ui.quiz

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.data.prefs.QuizForm
import com.dero.juststudykorean.data.quiz.Grade
import com.dero.juststudykorean.data.quiz.QuizRun
import com.dero.juststudykorean.data.quiz.ResultRow
import com.dero.juststudykorean.ui.components.HistoryLine
import com.dero.juststudykorean.ui.components.JsBottomBar
import com.dero.juststudykorean.ui.components.MistakeLine
import com.dero.juststudykorean.ui.navigation.TopLevel
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Quiz with fixed fake data, at 411 dp. Compare with design/png/quiz-*.png. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class QuizScreenshotTest {
    private val mistakes = listOf(
        MistakeLine("Unit 3 · At school.pdf", "Nov 6, 9:40 PM · Multiple choice · wrong 2×"),
        MistakeLine("Lesson 5 · Verbs.pdf", "Nov 5, 7:12 PM · Write in Korean"),
        MistakeLine("Grammar week 14.pdf", "Nov 4, 8:03 PM · Grammar selection"),
    )
    private val history = listOf(
        HistoryLine("run-3", 4, 5, "Unit 3 · At school", "Today, 8:10 PM"),
        HistoryLine("run-2", 2, 5, "All documents", "Yesterday, 9:32 PM"),
        HistoryLine("run-1", 5, 5, "Lesson 5 · Verbs", "Nov 6, 7:45 PM"),
    )

    private fun q(id: Long, type: String, prompt: String, answer: String, options: List<String>? = null) = QuestionsEntity(
        id, type, prompt, options?.joinToString(",", "[", "]") { "\"$it\"" }, answer, null, 1, null, "[\"1:4:0\"]", null, "rules", 0,
    )

    private val questions = listOf(
        q(1, "ko_to_en", "근처", "near, nearby"),
        q(2, "en_to_ko", "library", "도서관", listOf("도서관", "학교", "시장", "근처")),
        q(3, "vocab_recognition", "근처", "near, nearby", listOf("near, nearby", "library", "school", "market")),
        q(4, "write_ko", "library", "도서관"),
        q(5, "en_to_ko", "school", "학교", listOf("학교", "도서관", "시장", "근처")),
    )
    private val run = QuizRun(9, questions)
    private val docs = questions.associate { it.id to "Unit 3 · At school.pdf" }
    private fun answered(vararg pairs: Pair<Long, String>) = pairs.associate { (id, given) ->
        val qq = questions.first { it.id == id }
        id to Answered(given, Grade(given == qq.correctAnswer, qq.correctAnswer, null))
    }

    private fun snap(name: String, fontScale: Float = 1f, bar: Boolean = true, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$name.png") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                JustStudyTheme(darkTheme = false) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { content() }
                        if (bar) JsBottomBar(current = TopLevel.Quiz, onSelect = {})
                    }
                }
            }
        }

    @Composable
    private fun Home() = QuizHomeContent(QuizForm(), emptyList(), mistakes, history, remember { SnackbarHostState() }, {}, {}, {}, {}, {}, {})

    @Test
    fun homeTop() = snap("quiz_home") { Home() }

    @Test
    @Config(qualifiers = "w411dp-h2300dp-420dpi")
    fun homeFull() = snap("quiz_home_full") { Home() }

    @Test
    fun homeAt130Percent() = snap("quiz_home_130", fontScale = 1.3f) { Home() }

    @Test
    fun questionChoice() = snap("quiz_question", bar = false) {
        QuestionContent(QuestionUi(loading = false, run = run, index = 2, answers = answered(1L to "near, nearby", 2L to "도서관"), documents = docs), {}, {}, {}, {})
    }

    @Test
    fun questionWrongTyped() = snap("quiz_wrong", bar = false) {
        QuestionContent(QuestionUi(loading = false, run = run, index = 3, answers = answered(1L to "near, nearby", 2L to "도서관", 3L to "school", 4L to "도서간"), documents = docs), {}, {}, {}, {})
    }

    @Test
    fun leaveDialog() = snap("quiz_leave", bar = false) {
        QuestionContent(QuestionUi(loading = false, run = run, index = 2, answers = answered(1L to "near, nearby", 2L to "도서관"), documents = docs), {}, {}, {}, {}, leaveAtStart = true)
    }

    @Test
    fun results() = snap("quiz_results", bar = false) {
        ResultsContent(
            listOf(
                ResultRow(questions[0], "near, nearby", true), ResultRow(questions[1], "도서관", true),
                ResultRow(q(6, "fill_blank", "-아서/어서: 바쁘___ 못 가요", "바빠서"), "바쁘고", false),
                ResultRow(questions[3], "도서간", false), ResultRow(questions[4], "학교", true),
            ),
            {}, {},
        )
    }
}
