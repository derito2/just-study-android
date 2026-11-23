package com.dero.juststudykorean.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.KoSide
import com.dero.juststudykorean.ui.components.Pill
import com.dero.juststudykorean.ui.components.PillTone
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/** Who makes a question type (design/ajustes.md AJ-40): its Pill on the Quiz Guide. */
enum class GuidePill(val label: String, val tone: PillTone) {
    Rules("By rules", PillTone.Neutral), AI("On-device AI", PillTone.Info), Practice("Practice · not scored", PillTone.Warn),
}

data class TypeGuide(val pill: GuidePill, val text: String)

// design/ajustes.md AJ-41 (v3, checked against the code): next to QUIZ_TYPES and LEVELS so form and guide stay together.
val QUIZ_GUIDE = linkedMapOf(
    "ko_to_en" to TypeGuide(
        GuidePill.Rules,
        "Shows a Korean word from your notes; you type its English meaning. Made by rules from your word pairs, so Difficulty doesn't change it. " +
            "With On-device AI ready, an answer the rules reject but that means the same also counts.",
    ),
    "write_ko" to TypeGuide(
        GuidePill.Rules,
        "Shows an English meaning from your notes; you type the Korean. Spaces and punctuation don't count. Made by rules, so Difficulty doesn't change it.",
    ),
    "vocab_recognition" to TypeGuide(
        GuidePill.Rules,
        "Shows a Korean word; you pick its meaning from 4 options. The wrong ones are meanings of other words in the same document. " +
            "Made by rules, so Difficulty doesn't change it.",
    ),
    "en_to_ko" to TypeGuide(
        GuidePill.Rules,
        "Shows an English meaning; you pick the Korean word from 4 options taken from the same document. Made by rules, so Difficulty doesn't change it.",
    ),
    "fill_blank" to TypeGuide(
        GuidePill.Rules,
        "An example sentence of a grammar point in your notes, with its ending taken out; you pick the ending from 4 options " +
            "(endings of other grammar points in the same document). Made by rules, so Difficulty doesn't change it.",
    ),
    "multiple_choice" to TypeGuide(
        GuidePill.Rules,
        "4 options about your notes: mostly what a grammar pattern or particle means, or which sentence means something; when there aren't enough, " +
            "what one of your words means. With On-device AI ready, the model also writes some (up to 3 per quiz, shared with Short answer); " +
            "only those follow Difficulty.",
    ),
    "grammar_selection" to TypeGuide(
        GuidePill.Rules,
        "Shows what a grammar pattern or particle means; you pick it, e.g. -(으)니까. Usually 4 options; if a grammar point in your notes has its own " +
            "Anki options, you get those as you wrote them. Difficulty doesn't change it.",
    ),
    "short_answer" to TypeGuide(
        GuidePill.AI,
        "A short question about your notes; you type a short answer (a few words), in English or in Korean, as the question asks. The answer is always " +
            "in your notes. Only the model writes it (up to 3 per quiz, shared with Multiple choice), so it needs On-device AI ready and follows Difficulty.",
    ),
    SPEAK to TypeGuide(
        GuidePill.Practice,
        "You say a Korean word or sentence from your notes out loud; the app shows what it heard next to your notes. Practice only: it never counts " +
            "in your score. Needs the microphone and the phone's Korean speech recognition; the audio and what you said are never saved.",
    ),
)

const val DIFFICULTY_GUIDE = "Difficulty only changes the questions the model writes (some Multiple choice and all Short answer), so it only shows up " +
    "when On-device AI is ready and one of those types is checked. Pick one or more levels; with several, they take turns. Everything else is made " +
    "by rules from your notes and stays the same."

val LEVEL_GUIDE = linkedMapOf(
    1 to "Remember one item: what a word or pattern means, or the key word of a sentence.",
    2 to "Choose between look-alike options (e.g. 춥다 / 차갑다 / 시원하다), so you need to know the exact one.",
    3 to "Use a grammar rule: pick the right particle or ending for a sentence (e.g. 오니까 vs 와서). The answer is still in your notes.",
)

/** Quiz Guide (design/ajustes.md AJ-40): what each question type asks and what Difficulty changes. Only text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizGuideScreen(onMore: () -> Unit, onBack: () -> Unit, list: LazyListState = rememberLazyListState()) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(scroll.nestedScrollConnection)) {
        JsTopBar("Quiz Guide", "퀴즈 안내", "What each question type asks and what Difficulty changes.", null, scroll, onMore = onMore, onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize().testTag("quiz-guide"),
            state = list,
            contentPadding = PaddingValues(start = Tokens.Space4, end = Tokens.Space4, bottom = Tokens.Space6),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
        ) {
            item(key = "types") { SectionTitle("Question types", "문제 유형") }
            items(QUIZ_GUIDE.entries.toList(), key = { it.key }) { (id, guide) ->
                val (name, ko, hint) = QUIZ_TYPES.getValue(id)
                GuideCard {
                    Name(name, ko)
                    Text(hint, fontSize = 12.sp, color = JustStudyTheme.colors.muted)
                    Text(guide.text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Pill(guide.pill.label, guide.pill.tone)
                }
            }
            item(key = "difficulty") { SectionTitle("Difficulty", "난이도") }
            item(key = "levels") {
                GuideCard {
                    Text(DIFFICULTY_GUIDE, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    LEVELS.forEach { (n, label, ko) ->
                        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
                            Name(label, ko)
                            Text(LEVEL_GUIDE.getValue(n), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, ko: String) {
    Row(Modifier.padding(top = Tokens.Space1).semantics { heading() }, verticalAlignment = Alignment.Bottom) {
        Text(text, Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        KoSide(ko, MaterialTheme.typography.titleMedium.fontSize, Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun Name(text: String, ko: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text, Modifier.alignByBaseline(), style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
        KoSide(ko, JustStudyTheme.korean.bodyBold.fontSize, Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun GuideCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(Tokens.Border, JustStudyTheme.colors.line, MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large).padding(Tokens.Space4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space2),
    ) { content() }
}
