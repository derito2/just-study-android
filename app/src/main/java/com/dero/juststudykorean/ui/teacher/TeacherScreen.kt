package com.dero.juststudykorean.ui.teacher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.platform.testTag
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.korean.coach.CoachAnswer
import com.dero.juststudykorean.korean.coach.QuizProposal
import com.dero.juststudykorean.korean.coach.Step
import com.dero.juststudykorean.korean.teacher.Source
import com.dero.juststudykorean.korean.teacher.TeacherAnswer
import com.dero.juststudykorean.ui.components.ErrorState
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.SelectedGroup
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.components.StickerSize
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/** The three templates: label, Korean, and the text they put in the field with "___" selected (design/teacher.md, 4). */
internal val TEMPLATES = listOf(
    Triple("How do I say…?", "…을 어떻게 말해요?", "How do I say ___ in Korean?"),
    Triple("What does … mean?", "…은 무슨 뜻이에요?", "What does ___ mean?"),
    Triple("Example with…", "…로 예문", "Give me an example sentence with ___."),
)

/** A template's text with its gap selected, so typing fills it. */
internal fun template(text: String) = TextFieldValue(text, TextRange(text.indexOf("___"), text.indexOf("___") + 3))

/** Teacher (design/teacher.md): [coachAsks] grows when the Dashboard's "Ask what to do" opens it in the coach's mode. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeacherScreen(coachAsks: Int, onMore: () -> Unit, onOpenQuiz: (Long) -> Unit, onOpenFlashcards: () -> Unit, vm: TeacherViewModel = hiltViewModel()) {
    LaunchedEffect(coachAsks) { if (coachAsks > 0) vm.setMode(TeacherMode.Coach) }
    val ui by vm.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf(TextFieldValue()) }
    TeacherContent(
        ui, input, onInput = { input = it.copy(text = cut(it.text)) }, onSend = { vm.send(input.text); input = TextFieldValue() },
        onMode = vm::setMode, onClear = vm::clear, onRetry = vm::retry, onMore = onMore,
        onStartQuiz = { id, p -> vm.startQuiz(id, p, onOpenQuiz) }, onCreateDeck = vm::createDeck, onOpenFlashcards = onOpenFlashcards,
        keyboard = WindowInsets.isImeVisible,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherContent(
    ui: TeacherUi,
    input: TextFieldValue,
    onInput: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onMode: (TeacherMode) -> Unit,
    onClear: () -> Unit,
    onRetry: (Long) -> Unit,
    onMore: () -> Unit,
    onStartQuiz: (Long, QuizProposal) -> Unit,
    onCreateDeck: (Long) -> Unit,
    onOpenFlashcards: () -> Unit,
    openAll: Boolean = false, // screenshot tests: sources and "How I decided" already open
    keyboard: Boolean = false, // design/teacher.md 6: with the keyboard open, room for the conversation
) {
    val colors = JustStudyTheme.colors
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val list = rememberLazyListState()
    LaunchedEffect(ui.exchanges.size, ui.exchanges.lastOrNull()?.pending, keyboard) { if (ui.exchanges.isNotEmpty()) list.animateScrollToItem(ui.exchanges.size) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        JsTopBar(
            "Teacher", "선생님", "Your teacher answers from your own notes first. For anything else, AI is used.".takeIf { !keyboard },
            "선생님은 먼저 네 노트로 대답해. 나머지는 AI를 사용해.".takeIf { !keyboard }, scroll, onMore = onMore, onBack = null, forceFolded = keyboard,
        )
        Back(!keyboard) {
            SelectedGroup(
                listOf("Ask my notes", "What should I study?"), ui.mode.ordinal, { onMode(TeacherMode.entries[it]) },
                Modifier.padding(start = Tokens.Space4, end = Tokens.Space4, bottom = Tokens.Space2),
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().nestedScroll(scroll.nestedScrollConnection).testTag("conversation"),
            state = list,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Tokens.Space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space4),
        ) {
            if (ui.exchanges.isNotEmpty() && !keyboard) item(key = "clear") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    StickerButton("Clear answers", onClear, ko = "답변 지우기", color = StickerColor.Gray, size = StickerSize.Small)
                }
            }
            items(ui.exchanges, key = { it.id }) { e ->
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space4)) {
                    Mine(e.question)
                    Rising(e.id) {
                        when {
                            e.failed -> ErrorState("Teacher couldn't answer that.", onRetry = { onRetry(e.id) })
                            e.pending -> Text("Thinking… ${ui.thinkingSeconds} s", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                            e.notes != null -> AnswerCard(e.question, e.notes, openAll)
                            e.coach != null -> CoachCard(e, e.coach, onStartQuiz, onCreateDeck, onOpenFlashcards, openAll)
                        }
                    }
                }
            }
        }
        Composer(input, onInput, onSend, ui.busy, keyboard)
    }
}

/** What the open keyboard hides: gone at once, back with a 150 ms fade (instant with animations off). */
@Composable
private fun Back(visible: Boolean, content: @Composable () -> Unit) =
    AnimatedVisibility(visible, enter = fadeIn(tween(150)), exit = ExitTransition.None) { content() }

/** Each new answer comes in rising 8 dp (240 ms). */
@Composable
private fun Rising(key: Any, content: @Composable () -> Unit) {
    var shown by rememberSaveable(key) { mutableStateOf(false) }
    LaunchedEffect(key) { shown = true }
    val dy by animateDpAsState(if (shown) 0.dp else 8.dp, tween(240), label = "rise")
    Box(Modifier.offset(y = dy)) { content() }
}

/** Your question: a brand bubble on the right that hugs its text, up to 80 % of the width. */
@Composable
private fun Mine(text: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    Spacer(Modifier.weight(0.2f))
    Text(
        text, Modifier.weight(0.8f, fill = false).background(JustStudyTheme.colors.brand, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color.White, style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val colors = JustStudyTheme.colors
    Column(
        Modifier.fillMaxWidth(0.9f).background(colors.surface, MaterialTheme.shapes.large).border(Tokens.Border, colors.line, MaterialTheme.shapes.large).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space2),
    ) { content() }
}

@Composable
internal fun AnswerCard(question: String, answer: TeacherAnswer, openAll: Boolean = false) = Card {
    val colors = JustStudyTheme.colors
    Text(question, style = JustStudyTheme.korean.bodyBold, color = colors.muted)
    when (answer.status) {
        "not_found" -> Refusal("Your notes don't cover this", "Your teacher read the closest passages and the answer isn't there. Add it to your notes.")
        "no_context" -> Refusal("Nothing close enough in your notes", "Nothing in your documents is close enough. Try other words, or go write better notes.")
        else -> Text(answer.answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
    if (answer.sources.isNotEmpty()) SourceList(answer.sources, openAll)
}

@Composable
private fun Refusal(title: String, line: String) {
    Text(title, style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
    Text(line, style = MaterialTheme.typography.bodyMedium, color = JustStudyTheme.colors.muted)
}

/** Sources from your notes: one line per source, its whole passage on demand; the ones read but not used, folded. */
@Composable
internal fun SourceList(sources: List<Source>, openAll: Boolean = false) {
    val colors = JustStudyTheme.colors
    Box(Modifier.fillMaxWidth().height(Tokens.Border).background(colors.line))
    val (used, read) = sources.partition { it.cited }
    if (used.isNotEmpty()) {
        Text("Sources from your notes", style = MaterialTheme.typography.labelMedium, color = colors.muted, modifier = Modifier.semantics { heading() })
        used.forEach { SourceRow(it, openAll) }
    }
    if (read.isNotEmpty()) {
        var open by rememberSaveable { mutableStateOf(openAll) }
        Text(
            (if (open) "▾ " else "▸ ") + "Also read, not used in the answer", style = MaterialTheme.typography.labelMedium, color = colors.muted,
            modifier = Modifier.heightIn(min = 48.dp).clickable { open = !open }.padding(vertical = 12.dp),
        )
        if (open) read.forEach { SourceRow(it, openAll) }
    }
}

@Composable
private fun SourceRow(source: Source, openAll: Boolean) {
    val colors = JustStudyTheme.colors
    var open by rememberSaveable(source.hit.id) { mutableStateOf(openAll) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
            LucideIcon(Lucide.FileText, contentDescription = null, size = 16.dp, tint = colors.muted)
            val c = source.hit.chunk
            Text("[${source.n}] ${c.filename.substringBeforeLast('.')} · page ${c.pageNumber}", fontSize = 12.sp, color = colors.muted)
        }
        Text(
            if (open) "Hide the passage" else "Show the whole passage", style = MaterialTheme.typography.bodySmall, color = colors.brandText,
            modifier = Modifier.heightIn(min = 48.dp).clickable { open = !open }.padding(vertical = 12.dp),
        )
        if (open) Text(source.hit.chunk.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun CoachCard(
    e: Exchange,
    answer: CoachAnswer,
    onStartQuiz: (Long, QuizProposal) -> Unit,
    onCreateDeck: (Long) -> Unit,
    onOpenFlashcards: () -> Unit,
    openAll: Boolean = false,
) = Card {
    val colors = JustStudyTheme.colors
    Text(
        "COACH", style = MaterialTheme.typography.labelMedium, color = colors.fillWarn,
        modifier = Modifier.background(colors.warnFill, RoundedCornerShape(Tokens.RadiusSm)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
    Text(e.question, style = JustStudyTheme.korean.bodyBold, color = colors.muted)
    Text(answer.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    answer.quiz?.let { quiz ->
        Proposal("QUIZ", quiz.words) {
            StickerButton("Start quiz", { onStartQuiz(e.id, quiz) }, ko = "퀴즈 시작", color = StickerColor.Go)
            e.quizError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger) }
        }
    }
    answer.deck?.let { deck ->
        Proposal("NEW DECK", deck.words) {
            when (val state = e.deck) {
                is DeckState.Created -> StickerButton("Open flashcards", onOpenFlashcards, ko = "플래시카드 열기", color = StickerColor.Go)
                else -> StickerButton("Create deck", { onCreateDeck(e.id) }, ko = "덱 만들기", color = StickerColor.Action, enabled = state !is DeckState.Creating)
            }
            (e.deck as? DeckState.Failed)?.let { Text(it.message, style = MaterialTheme.typography.bodySmall, color = colors.danger) }
        }
    }
    if (answer.steps.isNotEmpty()) HowIDecided(answer.steps, openAll)
}

@Composable
private fun Proposal(label: String, words: List<String>?, actions: @Composable () -> Unit) {
    val colors = JustStudyTheme.colors
    Column(
        Modifier.fillMaxWidth().background(colors.tint, MaterialTheme.shapes.medium).border(Tokens.Border, colors.tintBorder, MaterialTheme.shapes.medium).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space2),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.brandText)
        if (!words.isNullOrEmpty()) {
            Text("Your words", style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
            Text(words.joinToString(", "), style = JustStudyTheme.korean.koBody, color = MaterialTheme.colorScheme.onSurface)
        }
        actions()
    }
}

private val TOOL_NAMES = mapOf(
    "get_weak_topics" to "Weak topics", "get_recent_progress" to "Recent progress", "search_knowledge_base" to "Your notes",
    "get_due_flashcards" to "Due flashcards", "propose_quiz" to "Quiz", "make_practice" to "Your words",
)

/** How I decided: the steps the rules took, folded. */
@Composable
private fun HowIDecided(steps: List<Step>, openAll: Boolean) {
    val colors = JustStudyTheme.colors
    var open by rememberSaveable { mutableStateOf(openAll) }
    Text(
        (if (open) "▾ " else "▸ ") + "How I decided", style = MaterialTheme.typography.labelMedium, color = colors.muted,
        modifier = Modifier.heightIn(min = 48.dp).clickable { open = !open }.padding(vertical = 12.dp),
    )
    if (open) steps.forEach { s ->
        Text("${if (s.ok) "✓" else "✕"} ${TOOL_NAMES[s.tool] ?: s.tool}: ${s.summary}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
    }
}

/** The templates, the field (1 to 4 lines, 500 characters) and Send, above the keyboard. */
@Composable
private fun Composer(input: TextFieldValue, onInput: (TextFieldValue) -> Unit, onSend: () -> Unit, busy: Boolean, keyboard: Boolean) {
    val colors = JustStudyTheme.colors
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Back(!keyboard || input.text.isEmpty()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Tokens.Space4, vertical = Tokens.Space2), horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
            TEMPLATES.forEach { (label, ko, text) ->
                Column(
                    Modifier.heightIn(min = 48.dp).background(colors.pill, RoundedCornerShape(Tokens.RadiusFull)).clickable { onInput(template(text)) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(label, style = JustStudyTheme.korean.chip, color = MaterialTheme.colorScheme.onSurface)
                    Text(ko, fontSize = 11.sp, color = colors.muted)
                }
            }
        }
        }
        Box(Modifier.fillMaxWidth().height(Tokens.Border).background(colors.line))
        Row(Modifier.fillMaxWidth().padding(Tokens.Space4), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
            BasicTextField(
                input, onInput, Modifier.weight(1f).background(colors.surface, MaterialTheme.shapes.medium).border(Tokens.Border, colors.line, MaterialTheme.shapes.medium)
                    .padding(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface), minLines = 1, maxLines = 4,
                cursorBrush = SolidColor(colors.brand),
                decorationBox = { inner ->
                    if (input.text.isEmpty()) Text("Ask something worth asking. For example: how do I say school?", style = MaterialTheme.typography.bodyMedium, color = colors.faint)
                    inner()
                },
            )
            StickerButton("Send", onSend, ko = "보내기", color = StickerColor.Go, enabled = canSend(input.text) && !busy)
        }
        Back(!keyboard) {
            Text("Answers come from all your documents.", fontSize = 12.sp, color = colors.muted, modifier = Modifier.padding(start = Tokens.Space4, bottom = Tokens.Space2))
        }
    }
}
