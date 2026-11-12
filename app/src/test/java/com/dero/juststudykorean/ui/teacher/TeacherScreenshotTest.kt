package com.dero.juststudykorean.ui.teacher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.korean.Chunk
import com.dero.juststudykorean.korean.coach.CoachAnswer
import com.dero.juststudykorean.korean.coach.DeckProposal
import com.dero.juststudykorean.korean.coach.QuizProposal
import com.dero.juststudykorean.korean.coach.Step
import com.dero.juststudykorean.korean.teacher.Hit
import com.dero.juststudykorean.korean.teacher.NOT_IN_NOTES
import com.dero.juststudykorean.korean.teacher.Source
import com.dero.juststudykorean.korean.teacher.TeacherAnswer
import com.dero.juststudykorean.ui.components.JsBottomBar
import com.dero.juststudykorean.ui.navigation.TopLevel
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Teacher with fixed fake data, at 411 dp (design/teacher.md; no PNG of its own: TeacherChat of the design system). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class TeacherScreenshotTest {
    private val chunk = Chunk(6, "Korean_Notes_Example.pdf", 1, 1, "Vocabulary", "Vocabulary", "vocabulary", "mixed",
        "Vocabulary\n1. 근처 = Nearby\n이 근처에는 카페가 없어요. = There are no cafes in this vicinity.\n학교가 집 근처에 있어요. = The school is nearby my house.")
    private val other = chunk.copy(index = 2, pageNumber = 2, text = "Sentences\n1. 피곤하니까 집에 갈래요 = Since I am tired, I want to go home.")
    private fun source(n: Int, c: Chunk, cited: Boolean = true) = Source(n, Hit(c.chromaId, c.text, 0.0, c), cited)

    private val entry = TeacherAnswer(
        "answered", "1. 근처 = Nearby [1]\n이 근처에는 카페가 없어요. = There are no cafes in this vicinity.\n학교가 집 근처에 있어요. = The school is nearby my house.",
        listOf(source(1, chunk), source(2, other, cited = false)), "entry.v1", "none (your entry)",
    )
    private val notCovered = TeacherAnswer("not_found", NOT_IN_NOTES, listOf(source(1, other, cited = false)), "tutor.v1", "none")
    private val coach = CoachAnswer(
        "Here is a quiz with 6 words you keep getting wrong: 빠르다, 거울, 가방, 날씨가 너무 덥다, 근처…. Press Start quiz.",
        listOf(Step("make_practice", mapOf("kind" to "quiz", "source" to "mistakes"), true, "quiz with 6 words: Your mistakes")),
        QuizProposal(null, "Your mistakes", listOf("빠르다", "거울", "가방", "근처"), 4, listOf("en_to_ko", "ko_to_en")),
        DeckProposal("Your mistakes", listOf("빠르다", "거울", "가방", "근처")), fallback = false,
    )

    private fun snap(name: String, ui: TeacherUi, input: String = "", fontScale: Float = 1f, openAll: Boolean = false) =
        captureRoboImage("src/test/screenshots/$name.png") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                JustStudyTheme(darkTheme = false) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { Content(ui, input, openAll) }
                        JsBottomBar(current = TopLevel.Teacher, onSelect = {})
                    }
                }
            }
        }

    @Composable
    private fun Content(ui: TeacherUi, input: String, openAll: Boolean) =
        TeacherContent(ui, TextFieldValue(input), {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, openAll)

    private fun one(e: Exchange, mode: TeacherMode = TeacherMode.Notes) = TeacherUi(mode, listOf(e))

    @Test fun teacherEmpty() = snap("teacher_empty", TeacherUi())

    @Test fun answerFromNotes() = snap("teacher_answer", one(Exchange(0, "What does 근처 mean?", notes = entry)))

    @Test fun answerWithSourcesOpen() = snap("teacher_answer_sources", one(Exchange(0, "What does 근처 mean?", notes = entry)), openAll = true)

    @Test fun notCovered() = snap("teacher_not_covered", one(Exchange(0, "How do I say spaceship?", notes = notCovered)))

    @Test fun coachWithQuizAndDeck() = snap("teacher_coach", one(Exchange(0, "Quiz on my mistakes", coach = coach), TeacherMode.Coach))

    @Test fun howIDecidedOpen() = snap("teacher_coach_decided", one(Exchange(0, "Quiz on my mistakes", coach = coach), TeacherMode.Coach), openAll = true)

    /** Robolectric has no keyboard: the composer with a question typed and Send enabled. */
    @Test fun composerKeyboard() = snap("teacher_composer", TeacherUi(), input = "How do I say school in Korean?")

    @Test fun teacherAt130Percent() = snap("teacher_130", one(Exchange(0, "What does 근처 mean?", notes = entry)), fontScale = 1.3f)
}
