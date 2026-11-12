package com.dero.juststudykorean.ui.quiz

import com.dero.juststudykorean.ui.components.AiMark
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.data.quiz.options
import com.dero.juststudykorean.ui.components.AnswerFeedback
import com.dero.juststudykorean.ui.components.ErrorState
import com.dero.juststudykorean.ui.components.JsDialog
import com.dero.juststudykorean.ui.components.JsTextField
import com.dero.juststudykorean.ui.components.KoSide
import com.dero.juststudykorean.ui.components.LoadingState
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.OptionState
import com.dero.juststudykorean.ui.components.QuizOption
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

@Composable
fun QuestionScreen(runId: Long?, onLeave: () -> Unit, onResults: (Long) -> Unit, vm: QuestionViewModel = hiltViewModel()) {
    LaunchedEffect(runId) { vm.open(runId) }
    val ui by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(ui.finished) { if (ui.finished) ui.run?.let { onResults(it.id) } }
    QuestionContent(ui, onLeave = onLeave, onType = vm::type, onAnswer = vm::answer, onNext = vm::next)
}

/** A smaller top bar: an icon button, the title and, on the right, a note. */
@Composable
internal fun SmallBar(icon: Lucide, iconLabel: String, title: String, note: String?, onIcon: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = Tokens.Space1, end = Tokens.Space4), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onIcon) { LucideIcon(icon, contentDescription = iconLabel, tint = ink) }
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = JustStudyTheme.colors.muted)
    }
}

/** The question (design/quiz.md B and C). [leaveAtStart]: for screenshot tests. */
@Composable
fun QuestionContent(ui: QuestionUi, onLeave: () -> Unit, onType: (String) -> Unit, onAnswer: (String) -> Unit, onNext: () -> Unit, leaveAtStart: Boolean = false) {
    val c = JustStudyTheme.colors
    var leaving by remember { mutableStateOf(leaveAtStart) }
    val run = ui.run
    BackHandler(enabled = run != null) { leaving = true }
    val total = (run?.questions?.size ?: 0) + ui.pendingAi // A.3: counts the ones the model is still writing
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        SmallBar(Lucide.X, "Leave quiz", "Quiz", if (run != null) "${ui.index + 1} of $total" else null) { if (run != null) leaving = true else onLeave() }
        when {
            ui.loading -> Box(Modifier.padding(Tokens.Space4)) { LoadingState("Creating your quiz…") }
            ui.failed || run == null -> Column(Modifier.padding(Tokens.Space4)) {
                ErrorState("Try other question types, another document, or try again.", onLeave, title = "No questions could be created this time.", retryLabel = "Back")
            }
            ui.question == null -> Box(Modifier.padding(Tokens.Space4)) { LoadingState("Writing the next question…") } // A.3
            else -> {
                val q = ui.question!!
                val answered = ui.answers[q.id]
                val done = ui.answers.size
                LinearProgressIndicator(
                    { done.toFloat() / total }, Modifier.fillMaxWidth().padding(horizontal = Tokens.Space4).height(6.dp),
                    color = c.brand, trackColor = c.canvas, gapSize = 0.dp, drawStopIndicator = {}, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                if (ui.pendingAi > 0) Row(Modifier.padding(horizontal = Tokens.Space4, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
                    Text("Writing ${ui.pendingAi} more with AI…", fontSize = 12.sp, color = c.muted)
                    AiMark(short = true)
                }
                Row(Modifier.fillMaxWidth().padding(Tokens.Space4)) {
                    Count("Done", "완료", "$done/$total", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    Count("Correct", "정답", "${ui.correct}", c.correct, Modifier.weight(1f))
                    Count("Left", "남음", "${total - done}", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
                val mode = answerMode(q.type)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space4)) {
                    Column(
                        Modifier.fillMaxWidth().border(Tokens.Border, c.line, MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large).padding(Tokens.Space5),
                        verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
                    ) {
                        val type = QUIZ_TYPES[q.type]
                        Row(verticalAlignment = Alignment.Bottom) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                                Text(type?.first ?: q.type, Modifier.alignByBaseline(), style = MaterialTheme.typography.bodySmall, color = c.muted)
                                if (type != null) KoSide(type.second, 12.sp, Modifier.alignByBaseline(), color = c.muted)
                            }
                            if (q.id < 0) AiMark(Modifier.padding(end = Tokens.Space2), short = true) // written by the model (kept in memory only)
                            ui.documents[q.id]?.let { Text(it.substringBeforeLast("."), style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        val korean = Regex("[가-힣]").containsMatchIn(q.prompt)
                        Text(q.prompt, style = if (korean) JustStudyTheme.korean.koPrompt else TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        type?.let { Text("${it.third}.", style = MaterialTheme.typography.bodySmall, color = c.muted) }
                        if (mode == "choice") {
                            val options = q.options()
                            options.forEachIndexed { i, o ->
                                val state = when {
                                    answered == null -> OptionState.Open
                                    o == answered.grade.correctAnswer -> OptionState.Correct
                                    o == answered.given -> OptionState.Chosen
                                    else -> OptionState.Other
                                }
                                QuizOption(i + 1, o, state, { onAnswer(o) })
                            }
                        } else {
                            Text(if (mode == "korean") "Your answer in Korean" else "Your answer", style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
                            JsTextField(
                                answered?.given ?: ui.typed, onType, Modifier.fillMaxWidth(), enabled = answered == null,
                                borderColor = if (answered != null && !answered.grade.correct) c.wrongBorder else null,
                                style = if (mode == "korean") JustStudyTheme.korean.koBody else MaterialTheme.typography.bodyMedium,
                                onDone = { if (ui.typed.isNotBlank()) onAnswer(ui.typed) },
                            )
                            if (answered != null) AnswerFeedback(answered.grade.correct, answered.given, answered.grade.correctAnswer, korean = mode == "korean")
                            if (answered?.grade?.aiAccepted == true) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
                                Text("accepted: same meaning", fontSize = 12.sp, color = c.correct)
                                AiMark(short = true)
                            }
                        }
                        answered?.explanation?.let { MistakeExplanation(it, answered.aiExplanation) }
                    }
                }
                ui.error?.let { Text(it, Modifier.padding(horizontal = Tokens.Space4), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Box(Modifier.fillMaxWidth().padding(Tokens.Space4)) {
                    when {
                        answered != null -> if (ui.index + 1 < total) {
                            StickerButton("Next question", onNext, Modifier.fillMaxWidth(), ko = "다음 문제")
                        } else {
                            StickerButton("See results", onNext, Modifier.fillMaxWidth(), ko = "결과 보기")
                        }
                        mode != "choice" -> StickerButton("Check", { onAnswer(ui.typed) }, Modifier.fillMaxWidth(), ko = "확인", enabled = ui.typed.isNotBlank())
                    }
                }
            }
        }
    }
    if (leaving) {
        JsDialog(
            null, null, "Leave this quiz? The answers you already gave are saved, but the rest of the quiz is lost.", { leaving = false },
            confirm = { m -> StickerButton("Leave", { leaving = false; onLeave() }, m, color = StickerColor.Danger) },
            dismiss = { m -> StickerButton("Stay", { leaving = false }, m, color = StickerColor.Gray) },
        )
    }
}

@Composable
private fun Count(label: String, ko: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.clearAndSetSemantics { contentDescription = "$label: $value" }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label.uppercase(), Modifier.alignByBaseline(), style = MaterialTheme.typography.labelMedium, color = JustStudyTheme.colors.muted)
            KoSide(ko, MaterialTheme.typography.labelMedium.fontSize, Modifier.alignByBaseline(), color = JustStudyTheme.colors.muted)
        }
        Text(value, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = color)
    }
}

/** Why you got it wrong (design/quiz.md A.2): in a `canvas` box, with AiMark when the model wrote it. */
@Composable
private fun MistakeExplanation(text: String, ai: Boolean) {
    val c = JustStudyTheme.colors
    Column(
        Modifier.fillMaxWidth().background(c.canvas, MaterialTheme.shapes.medium).padding(Tokens.Space3),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space2),
    ) {
        if (ai) AiMark()
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

