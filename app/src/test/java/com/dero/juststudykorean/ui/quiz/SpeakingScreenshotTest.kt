package com.dero.juststudykorean.ui.quiz

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.data.prefs.QuizForm
import com.dero.juststudykorean.data.quiz.QuizRun
import com.dero.juststudykorean.data.quiz.ResultRow
import com.dero.juststudykorean.data.quiz.SpokenItem
import com.dero.juststudykorean.korean.speech.compareSpoken
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** design/quiz-speaking.md H: the spoken practice, light and dark, frozen motion. Invented content. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class SpeakingScreenshotTest {
    private fun q(id: Long, english: String, korean: String) =
        QuestionsEntity(id, "write_ko", "Write in Korean: \"$english\"", null, korean, null, 1, null, "[\"1:4:0\"]", null, "rules", 0)

    private val word = q(4, "Library", "도서관")
    private val sentence = q(5, "I go to school", "저는 학교에 가요")
    private fun ui(question: QuestionsEntity) =
        QuestionUi(loading = false, run = QuizRun(9, listOf(question, word)), documents = mapOf(question.id to "Unit 3 · At school.pdf"), spoken = setOf(question.id))

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name${if (dark) "_dark" else ""}.png") {
            CompositionLocalProvider(LocalFrozenMotion provides true) { JustStudyTheme(darkTheme = dark) { content() } }
        }
    }

    private fun question(name: String, s: SpeakingUi, question: QuestionsEntity = word, permissionNeeded: Boolean = false) = listOf(false, true).forEach { dark ->
        snap(name, dark) { QuestionContent(ui(question), {}, {}, {}, {}, speaking = s, voiceReady = true, permissionNeeded = permissionNeeded) }
    }

    @Test fun ready() = question("quiz_speak_ready", SpeakingUi(), permissionNeeded = true)
    @Test fun listening() = question("quiz_speak_listening", SpeakingUi(phase = SpeakPhase.Listening, level = 0.5f))
    @Test fun comparedWithDifferences() = question(
        "quiz_speak_compared", SpeakingUi(phase = SpeakPhase.Compared, attempts = 1, comparison = compareSpoken("저는 학교에 가요", listOf("저는 학교를 가요"))), sentence,
    )
    @Test fun comparedMatches() = question("quiz_speak_matches", SpeakingUi(phase = SpeakPhase.Compared, attempts = 2, comparison = compareSpoken("도서관", listOf("도서관"))))
    @Test fun permissionDenied() = question("quiz_speak_denied", SpeakingUi(phase = SpeakPhase.PermissionDenied))

    private val normal = ResultRow(q(1, "School", "학교").copy(type = "en_to_ko", prompt = "Which Korean word means \"School\"?"), "학교", true)

    @Test fun resultsWithSpokenPractice() = listOf(false, true).forEach { dark ->
        snap("quiz_results_spoken", dark) { ResultsContent(listOf(normal), {}, {}, spoken = listOf(SpokenItem(word, "도서권"), SpokenItem(sentence, null))) }
    }

    @Test fun practiceOnly() = listOf(false, true).forEach { dark ->
        snap("quiz_results_practice_only", dark) { ResultsContent(emptyList(), {}, {}, spoken = listOf(SpokenItem(word, "도서관"), SpokenItem(sentence, "저는 학교를 가요"))) }
    }

    @Composable
    private fun Form(speech: Boolean, checked: Boolean) = QuizHomeContent(
        QuizForm(types = if (checked) setOf("write_ko", SPEAK) else setOf("write_ko")), emptyList(), emptyList(), emptyList(), remember { SnackbarHostState() },
        {}, {}, {}, {}, {}, {}, photo = false, speech = speech,
    )

    @Test fun formCheckbox() = listOf(false, true).forEach { dark ->
        snap("quiz_form_speak_on", dark) { Form(speech = true, checked = true) }
        snap("quiz_form_speak_off", dark) { Form(speech = false, checked = false) }
    }
}
