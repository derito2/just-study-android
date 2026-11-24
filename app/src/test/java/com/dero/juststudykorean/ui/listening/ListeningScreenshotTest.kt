package com.dero.juststudykorean.ui.listening

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.korean.compareAnswer
import com.dero.juststudykorean.korean.listening.LabSentence
import com.dero.juststudykorean.korean.listening.ListenMode
import com.dero.juststudykorean.ui.components.speech.SpeakPhase
import com.dero.juststudykorean.ui.components.speech.SpeakingUi
import com.dero.juststudykorean.korean.speech.SpokenComparison
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** design/beta.md 4: Listening Lab's three modes and a dictation result, light and dark, at 411 dp (no bottom bar). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class ListeningScreenshotTest {
    private val sentence = LabSentence("s:1", "학교에 가요.", "I go to school.")
    private val base = ListeningUi(loading = false, items = listOf(sentence))

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) =
        // The microphone block has endless animations: frozen on their first frame, as in the Quiz's captures (else CI waits minutes).
        captureRoboImage("src/test/screenshots/$name.png") { CompositionLocalProvider(LocalFrozenMotion provides true) { JustStudyTheme(darkTheme = dark) { content() } } }

    @Composable
    private fun Lab(ui: ListeningUi) = ListeningContent(ui, SpeakingUi(), systemKeyboard = false, voiceReady = true, permissionNeeded = true)

    private val result = base.copy(typed = "학교에 가여", result = DictationResult("학교에 가여", false, compareAnswer("학교에 가여", "학교에 가요.", korean = true)))
    private val hands = base.copy(mode = ListenMode.HandsFree, items = listOf(LabSentence("w:근처", "근처", "near, nearby")), dueCount = 12, running = true, showMeaning = true)

    @Test fun dictation() = snap("listening_dictation", false) { Lab(base.copy(typed = "학교에")) }
    @Test fun dictationDark() = snap("listening_dictation_dark", true) { Lab(base.copy(typed = "학교에")) }
    @Test fun dictationResult() = snap("listening_dictation_result", false) { Lab(result) }
    @Test fun dictationResultDark() = snap("listening_dictation_result_dark", true) { Lab(result) }
    @Test fun shadowing() = snap("listening_shadowing", false) { Lab(base.copy(mode = ListenMode.Shadowing)) }
    @Test fun shadowingDark() = snap("listening_shadowing_dark", true) { Lab(base.copy(mode = ListenMode.Shadowing)) }
    // The owner's phone test: "Matches your notes" once (the banner), not again inside the comparison.
    private val matched = SpokenComparison("학교에 가요", "학교에 가요", true, emptyList(), emptyList())
    @Test fun shadowingMatched() = snap("listening_shadowing_matched", false) { ListeningContent(base.copy(mode = ListenMode.Shadowing), SpeakingUi(phase = SpeakPhase.Compared, comparison = matched, attempts = 1), false, voiceReady = true, permissionNeeded = false) }
    @Test fun shadowingMatchedDark() = snap("listening_shadowing_matched_dark", true) { ListeningContent(base.copy(mode = ListenMode.Shadowing), SpeakingUi(phase = SpeakPhase.Compared, comparison = matched, attempts = 1), false, voiceReady = true, permissionNeeded = false) }

    @Test fun handsFree() = snap("listening_handsfree", false) { Lab(hands) }
    @Test fun handsFreeDark() = snap("listening_handsfree_dark", true) { Lab(hands) }
}
