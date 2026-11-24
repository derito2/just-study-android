package com.dero.juststudykorean.ui.grammarlab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.korean.compareAnswer
import com.dero.juststudykorean.korean.grammar.Drill
import com.dero.juststudykorean.korean.hangul.HangulIme
import com.dero.juststudykorean.ui.components.AiMark
import com.dero.juststudykorean.ui.components.BannerTone
import com.dero.juststudykorean.ui.components.EmptyState
import com.dero.juststudykorean.ui.components.JsSheet
import com.dero.juststudykorean.ui.components.JsTextField
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.LoadingState
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.OptionState
import com.dero.juststudykorean.ui.components.Pill
import com.dero.juststudykorean.ui.components.PillTone
import com.dero.juststudykorean.ui.components.QuizOption
import com.dero.juststudykorean.ui.components.ResultBanner
import com.dero.juststudykorean.ui.components.SelectedGroup
import com.dero.juststudykorean.ui.components.Stat
import com.dero.juststudykorean.ui.components.StatStrip
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.components.StickerSize
import com.dero.juststudykorean.ui.components.TypedResult
import com.dero.juststudykorean.ui.components.softBox
import com.dero.juststudykorean.ui.games.pieces.HangulKeyboard
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

@Composable
fun GrammarLabScreen(pointKey: String?, onBack: () -> Unit, onLibrary: () -> Unit, vm: GrammarLabViewModel = hiltViewModel()) {
    LaunchedEffect(pointKey) { if (vm.state.value.drills.isEmpty()) vm.open(pointKey) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val system by vm.systemKeyboard.collectAsStateWithLifecycle()
    GrammarLabContent(
        ui, system, vm.aiReady(), onBack, onLibrary, vm::setPoint, vm::setLevel, vm::setSystemKeyboard, vm::type, { vm.check(it) },
        vm::next, vm::explainMore, vm::practiceMissed,
    )
}

private fun Drill.right() = when (this) { is Drill.Choose -> answer; is Drill.Produce -> answers.first(); is Drill.FillExample -> answers.first() }

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().border(Tokens.Border, JustStudyTheme.colors.line, MaterialTheme.shapes.large)
        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large).padding(Tokens.Space4),
    verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
) { content() }

/** Grammar Lab (design/beta.md 3): header (point, level), the drill, its result, the summary; the empty state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarLabContent(
    ui: GrammarLabUi,
    systemKeyboard: Boolean,
    aiReady: Boolean,
    onBack: () -> Unit,
    onLibrary: () -> Unit,
    onPoint: (String?) -> Unit,
    onLevel: (Int) -> Unit,
    onSystemKeyboard: (Boolean) -> Unit,
    onType: (String) -> Unit,
    onCheck: (String) -> Unit,
    onNext: () -> Unit,
    onExplainMore: () -> Unit,
    onPracticeMissed: () -> Unit,
) {
    val colors = JustStudyTheme.colors
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding().nestedScroll(scroll.nestedScrollConnection)) {
        JsTopBar("Grammar Lab", "문법 연습", "Make the forms yourself with your own verbs.", null, scroll, onMore = null, onBack = onBack)
        val pad = Modifier.padding(horizontal = Tokens.Space4)
        when {
            ui.loading -> LoadingState(modifier = pad)
            ui.points.isEmpty() || ui.empty && ui.pointKey == null -> EmptyState(
                Lucide.Dumbbell, "Nothing to practice yet", null,
                "Add grammar points with a Structure line (e.g. \"- A/V + 아서\") and some verbs to your notes.", pad,
                action = "Open Library", onAction = onLibrary, actionColor = StickerColor.Action,
            )
            else -> {
                val drill = ui.drill
                val typing = drill != null && drill !is Drill.Choose && ui.result == null
                // Produce and FillExample type with HangulKeys by default: one IME per drill.
                val ime = remember(ui.index, ui.drills) { HangulIme() }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).then(pad), verticalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
                    Card {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).border(Tokens.Border, colors.line, MaterialTheme.shapes.medium)
                                .clickable(role = Role.Button, onClickLabel = "Choose a grammar point") { picking = true }.padding(horizontal = Tokens.Space4, vertical = Tokens.Space3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(ui.points.firstOrNull { it.key == ui.pointKey }?.pattern ?: "All grammar", Modifier.weight(1f), style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
                            LucideIcon(Lucide.ChevronLeft, contentDescription = null, size = 20.dp, tint = colors.muted, modifier = Modifier.rotate(-90f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Level", Modifier.width(56.dp), style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
                            SelectedGroup(listOf("1", "2", "3"), ui.level - 1, { onLevel(it + 1) }, Modifier.weight(1f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${ui.points.size} of your grammar points can be practiced here.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                            Box(
                                Modifier.size(48.dp).clickable(role = Role.Switch) { onSystemKeyboard(!systemKeyboard) }
                                    .semantics { contentDescription = if (systemKeyboard) "Use the Hangul keys" else "Use the phone's keyboard" },
                                contentAlignment = Alignment.Center,
                            ) { LucideIcon(Lucide.Keyboard, contentDescription = null, tint = if (systemKeyboard) colors.brand else colors.control) }
                        }
                    }
                    when {
                        ui.done -> Summary(ui, onPracticeMissed, onBack)
                        drill != null -> Exercise(ui, drill, systemKeyboard, aiReady, onType, onCheck, onExplainMore)
                    }
                }
                if (drill != null && !ui.done) {
                    if (typing && !systemKeyboard) {
                        HangulKeyboard(
                            onJamo = { ime.input(it); onType(ime.text) }, onSpace = { ime.space(); onType(ime.text) }, onDelete = { ime.delete(); onType(ime.text) },
                            onCheck = { onCheck(ui.typed) }, checkEnabled = ui.typed.isNotBlank(), enabled = true,
                        )
                    }
                    if (ui.result != null || (typing && systemKeyboard)) Box(Modifier.fillMaxWidth().padding(Tokens.Space3)) {
                        when {
                            ui.result != null -> StickerButton("Next", onNext, Modifier.fillMaxWidth(), ko = "다음", color = StickerColor.Go, size = StickerSize.Large)
                            // With HangulKeys, their own Check key is the Check (no second button under them).
                            typing && systemKeyboard -> StickerButton("Check", { onCheck(ui.typed) }, Modifier.fillMaxWidth(), ko = "확인", color = StickerColor.Go, size = StickerSize.Large, enabled = ui.typed.isNotBlank())
                        }
                    }
                }
            }
        }
    }
    if (picking) {
        JsSheet(onDismiss = { picking = false }, title = "Grammar point", ko = "문법") {
            (listOf(null to "All grammar") + ui.points.map { it.key to "${it.pattern} · ${it.meaning}" }).forEach { (key, label) ->
                Text(
                    label,
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { picking = false; onPoint(key) }.padding(horizontal = Tokens.Space6, vertical = Tokens.Space3),
                    style = if (key == ui.pointKey) JustStudyTheme.korean.bodyBold else MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Exercise(ui: GrammarLabUi, drill: Drill, systemKeyboard: Boolean, aiReady: Boolean, onType: (String) -> Unit, onCheck: (String) -> Unit, onExplainMore: () -> Unit) {
    val colors = JustStudyTheme.colors
    val result = ui.result
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
            val meaning = ui.verbs[drill.verb]?.meaning?.lowercase()
            Text(drill.verb + (meaning?.let { " · $it" } ?: ""), Modifier.weight(1f, fill = false), style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
            Pill(drill.ending.label, PillTone.Info)
        }
        if (drill is Drill.FillExample) {
            Text(drill.before + "____" + drill.after, style = JustStudyTheme.korean.koTitle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            drill.translation?.let { Text(it, fontSize = 14.sp, color = colors.muted) }
        }
        when (drill) {
            is Drill.Choose -> drill.options.forEachIndexed { i, option ->
                val state = when {
                    result == null -> OptionState.Open
                    option == drill.answer -> OptionState.Correct
                    option == result.given -> OptionState.Chosen
                    else -> OptionState.Other
                }
                QuizOption(i + 1, option, state, { if (result == null) onCheck(option) })
            }
            else -> if (result == null) {
                if (systemKeyboard) JsTextField(ui.typed, onType, placeholder = "Type the form", center = true, onDone = { onCheck(ui.typed) })
                else Text(ui.typed.ifEmpty { " " }, Modifier.fillMaxWidth().softBox().padding(Tokens.Space3), style = JustStudyTheme.korean.koPrompt, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    if (result != null) {
        ResultBanner(if (result.correct) BannerTone.Correct else BannerTone.Wrong, if (result.correct) "Correct!" else "Not quite", ko = if (result.correct) "정답" else "오답")
        if (drill !is Drill.Choose) TypedResult(result.given, compareAnswer(result.given, drill.right(), korean = true))
        result.explanation?.let { Text(it, Modifier.fillMaxWidth().softBox().padding(Tokens.Space3), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
        if (result.ai != null) {
            Column(Modifier.fillMaxWidth().softBox().padding(Tokens.Space3), verticalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
                AiMark()
                Text(result.ai, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        } else if (!result.correct && aiReady) {
            StickerButton("Explain more", onExplainMore, color = StickerColor.Gray, size = StickerSize.Small, enabled = !result.explaining)
        }
    }
}

@Composable
private fun Summary(ui: GrammarLabUi, onPracticeMissed: () -> Unit, onDone: () -> Unit) {
    val right = ui.answers.count { it.second }
    Card {
        StatStrip(listOf(Stat("Done", "${ui.answers.size}"), Stat("Correct", "$right"), Stat("Missed", "${ui.answers.size - right}")))
        ui.answers.groupBy { it.first.ending }.forEach { (ending, list) ->
            Row {
                Text(ending.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("${list.count { it.second }}/${list.size}", style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.size(Tokens.Space1))
        if (right < ui.answers.size) StickerButton("Practice the ones I missed", onPracticeMissed, Modifier.fillMaxWidth(), color = StickerColor.Action)
        StickerButton("Done", onDone, Modifier.fillMaxWidth(), ko = "완료", color = StickerColor.Gray)
    }
}
