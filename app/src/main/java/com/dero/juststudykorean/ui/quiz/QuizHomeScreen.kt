package com.dero.juststudykorean.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.prefs.QuizForm
import com.dero.juststudykorean.ui.components.DocumentsField
import com.dero.juststudykorean.ui.components.DocumentsSheet
import com.dero.juststudykorean.ui.components.HistoryLine
import com.dero.juststudykorean.ui.components.JsCheckbox
import com.dero.juststudykorean.ui.components.JsSnackbarHost
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.KoSide
import com.dero.juststudykorean.ui.components.MistakeLine
import com.dero.juststudykorean.ui.components.QuizHistoryCard
import com.dero.juststudykorean.ui.components.Stepper
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.components.WeaknessPanel
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

@Composable
fun QuizHomeScreen(onMore: () -> Unit, onGenerate: () -> Unit, onOpenRun: (Long) -> Unit, onDetails: (String) -> Unit, vm: QuizHomeViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val documents by vm.documents.collectAsStateWithLifecycle()
    val mistakes by vm.mistakes.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.messages.collect { e -> when (e) { is QuizEvent.Open -> onOpenRun(e.runId); is QuizEvent.Message -> snackbar.showSnackbar(e.text) } }
    }
    QuizHomeContent(
        form ?: return, documents, mistakes, history, snackbar, onMore = onMore, onForm = { vm.setForm(it) }, onGenerate = onGenerate,
        onPracticeSaved = { vm.practiceSaved() }, onFix = { vm.fixWeaknesses() }, onDetails = onDetails,
    )
}

/** Quiz home (design/quiz.md A): New quiz, Quiz for your weaknesses and Quiz history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizHomeContent(
    form: QuizForm,
    documents: List<DocumentsEntity>,
    mistakes: List<MistakeLine>?,
    history: List<HistoryLine>,
    snackbar: SnackbarHostState,
    onMore: () -> Unit,
    onForm: (QuizForm) -> Unit,
    onGenerate: () -> Unit,
    onPracticeSaved: () -> Unit,
    onFix: () -> Unit,
    onDetails: (String) -> Unit,
    photo: Boolean = true,
) {
    val c = JustStudyTheme.colors
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var documentsOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
            JsTopBar("Quiz", "퀴즈", "Questions built only from your own notes. No excuses if you fail.", "네 노트로만 만든 문제. 틀려도 변명은 없어.", scroll, onMore = onMore, onBack = null)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Tokens.Space4, end = Tokens.Space4, bottom = Tokens.Space6),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
            ) {
                Column(
                    Modifier.fillMaxWidth().border(Tokens.Border, c.line, MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large).padding(Tokens.Space4),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space4),
                ) {
                    Title("New quiz", "새 퀴즈", MaterialTheme.typography.titleMedium.fontSize)
                    Column {
                        Label("Questions", "문제 수")
                        Stepper(form.count, { onForm(form.copy(count = it)) }, 1..10, "Questions")
                    }
                    Column {
                        Label("Documents", "문서")
                        DocumentsField(form.documentIds, documents, { documentsOpen = true })
                    }
                    Column {
                        Label("Question types", "문제 유형")
                        // design/quiz.md A.1: until the phone has a model, the types it writes are shown but cannot be used.
                        Grid(QUIZ_TYPES.entries.toList()) { (id, t) ->
                            if (id in MODEL_TYPES) CheckItem(t.first, t.second, NEEDS_AI, checked = false, unavailable = true, modifier = Modifier.alpha(0.5f)) {}
                            else CheckItem(t.first, t.second, t.third, id in form.types) { onForm(form.copy(types = if (id in form.types) form.types - id else form.types + id)) }
                        }
                        if (form.usableTypes().isEmpty()) Text("Choose at least one question type.", style = MaterialTheme.typography.bodyMedium, color = c.wrong)
                    }
                    Column(Modifier.alpha(0.5f)) {
                        Label("Difficulty", "난이도")
                        Text(NEEDS_AI, style = MaterialTheme.typography.bodySmall, color = c.muted)
                        Grid(LEVELS) { (n, label, ko) -> CheckItem(label, ko, null, n in form.levels, unavailable = true) {} } // shows what is saved
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
                        val ok = form.usableTypes().isNotEmpty()
                        StickerButton("Generate quiz", onGenerate, Modifier.fillMaxWidth(), ko = "퀴즈 만들기", color = StickerColor.Go, enabled = ok)
                        StickerButton("Practice saved questions", onPracticeSaved, Modifier.fillMaxWidth(), ko = "저장된 문제 연습")
                    }
                }
                WeaknessPanel(mistakes, onFix, photo = photo)
                QuizHistoryCard(history, onDetails)
            }
        }
        JsSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (documentsOpen) DocumentsSheet(documents, form.documentIds, { onForm(form.copy(documentIds = it)) }, { documentsOpen = false })
}

@Composable
private fun Title(text: String, ko: String, size: androidx.compose.ui.unit.TextUnit) {
    Row(Modifier.semantics { heading() }, verticalAlignment = Alignment.Bottom) {
        Text(text, Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium.copy(fontSize = size), color = MaterialTheme.colorScheme.onSurface)
        KoSide(ko, size, Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Label(text: String, ko: String) {
    Row(Modifier.padding(bottom = Tokens.Space2), verticalAlignment = Alignment.Bottom) {
        Text(text, Modifier.alignByBaseline(), style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
        KoSide(ko, 14.sp, Modifier.alignByBaseline(), color = JustStudyTheme.colors.muted)
    }
}

/** Two columns; each row as tall as its tallest item. */
@Composable
private fun <T> Grid(items: List<T>, item: @Composable (T) -> Unit) {
    Column {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
                row.forEach { Box(Modifier.weight(1f)) { item(it) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A checkbox row: the 18 dp box, the name (`body-bold`), its Korean (11 sp) and the hint (12 sp, `muted`). The whole row toggles. */
private const val NEEDS_AI = "Needs on-device AI"

/** [unavailable]: it does not answer to touch, and TalkBack says why. */
@Composable
private fun CheckItem(name: String, ko: String, hint: String?, checked: Boolean, modifier: Modifier = Modifier, unavailable: Boolean = false, onToggle: () -> Unit) {
    val c = JustStudyTheme.colors
    val touch = if (unavailable) {
        Modifier.clearAndSetSemantics { contentDescription = "$name, needs on-device AI, unavailable"; role = Role.Checkbox; disabled() }
    } else Modifier.toggleable(checked, role = Role.Checkbox) { onToggle() }
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).then(touch).padding(vertical = Tokens.Space1),
        verticalAlignment = Alignment.Top,
    ) {
        JsCheckbox(checked, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(Tokens.Space2))
        Column {
            Text(name, style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
            Text(ko, style = JustStudyTheme.korean.koBelow.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}

