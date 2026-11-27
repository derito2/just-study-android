package com.dero.juststudykorean.ui.mistakes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.korean.compareAnswer
import com.dero.juststudykorean.korean.mistakes.Mistake
import com.dero.juststudykorean.korean.mistakes.Pattern
import com.dero.juststudykorean.ui.components.EmptyState
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.LoadingState
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.Pill
import com.dero.juststudykorean.ui.components.PillTone
import com.dero.juststudykorean.ui.components.SelectedGroup
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.TypedResult
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalKoreanTypography
import com.dero.juststudykorean.ui.theme.Tokens

private val HANGUL = Regex("[가-힣]")

@Composable
fun MistakesScreen(onBack: () -> Unit, onGrammarLab: (String?) -> Unit, onDictation: () -> Unit, onQuiz: (Long) -> Unit, vm: MistakesViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.setPeriod(vm.state.value.period) }
    val ui by vm.state.collectAsStateWithLifecycle()
    MistakesContent(
        ui, onBack, onPeriod = { vm.setPeriod(it) },
        onPractice = { p ->
            when (practiceOf(p.kind)) {
                Practice.GrammarLab -> onGrammarLab(vm.pointOf(p))
                Practice.Dictation -> vm.dictation(onDictation)
                Practice.Words -> vm.quizMe(p, onQuiz)
            }
        },
        onDeck = { vm.makeDeck(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistakesContent(ui: MistakesUi, onBack: () -> Unit, onPeriod: (Int) -> Unit, onPractice: (Pattern) -> Unit, onDeck: (Pattern) -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pad = Modifier.padding(horizontal = Tokens.Space4)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().nestedScroll(scroll.nestedScrollConnection)) {
        JsTopBar("My Mistakes", "내 실수", "Where you keep slipping, from every part of the app.", null, scroll, onMore = null, onBack = onBack)
        SelectedGroup(listOf("7 days", "30 days", "All"), ui.period, onPeriod, pad.padding(bottom = Tokens.Space3))
        ui.message?.let { Pill(it, PillTone.Info, pad.padding(bottom = Tokens.Space3)) }
        when {
            ui.loading -> LoadingState(modifier = pad)
            ui.patterns.isEmpty() -> EmptyState(Lucide.NotebookPen, "No mistakes yet in this period.", null, "Do a quiz or a few games and come back.", pad)
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = Tokens.Space4, end = Tokens.Space4, bottom = Tokens.Space4), verticalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
                items(ui.patterns, key = { it.kind }) { PatternCard(it, onPractice, onDeck) }
            }
        }
    }
}

@Composable
private fun PatternCard(p: Pattern, onPractice: (Pattern) -> Unit, onDeck: (Pattern) -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(Tokens.Border, JustStudyTheme.colors.line, MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large).padding(Tokens.Space4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.label, Modifier.weight(1f), style = LocalKoreanTypography.current.bodyBold, color = MaterialTheme.colorScheme.onSurface)
            Pill("×${p.count}", PillTone.Wrong)
        }
        p.examples.forEach { Example(it) }
        if (practiceOf(p.kind) == Practice.Words) Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
            StickerButton("Quiz me", { onPractice(p) })
            StickerButton("Make a deck", { onDeck(p) })
        } else StickerButton("Practice this", { onPractice(p) }, ko = "연습하기")
    }
}

@Composable
private fun Example(m: Mistake) {
    val muted = JustStudyTheme.colors.muted
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        m.given?.let { TypedResult(it, compareAnswer(it, m.expected, korean = HANGUL.containsMatchIn(m.expected))) }
        Text("Expected: ${m.expected}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(m.source, style = MaterialTheme.typography.bodySmall, color = muted)
    }
}
