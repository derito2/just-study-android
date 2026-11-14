package com.dero.juststudykorean.ui.games.minigames

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.korean.games.Evaluators
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.games.RoundSpec.TypingMode
import com.dero.juststudykorean.korean.hangul.HangulIme
import com.dero.juststudykorean.ui.games.pieces.HangulKeyboard
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.games.talkBackOn
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.delay

/**
 * Hangul Typing (games-minigames.md 11): type with the games' own keyboard and Check. Level 1 copies; 2 from the
 * meaning; 3 from memory (the word shows 1500 ms) or a short sentence from its meaning. The moon's clock (6 s + 3 s
 * per block) on levels 2 and 3 when [timer] is on; when it runs out, what was typed is checked ("Time's up").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HangulTypingGame(spec: RoundSpec.HangulTyping, paused: Boolean, timer: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope) {
    val c = JustStudyTheme.colors
    val ime = remember { HangulIme() }
    var text by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf<Outcome?>(null) }
    var timedOut by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    val expected = spec.item.blocks.size
    val clocked = timer && spec.mode != TypingMode.COPY && !talkBackOn()
    val left = remember { Animatable(1f) }

    fun check(byClock: Boolean = false) {
        if (outcome != null) return
        timedOut = byClock && !Evaluators.typing(spec, text)
        outcome = if (Evaluators.typing(spec, text)) Outcome.WON else Outcome.LOST
        stage.timer = null
    }

    LaunchedEffect(spec) {
        stage.clear()
        if (spec.mode == TypingMode.FROM_MEMORY) { delay(1500); hidden = true }
    }
    LaunchedEffect(paused, hidden) {
        if (!clocked || paused || outcome != null || (spec.mode == TypingMode.FROM_MEMORY && !hidden)) return@LaunchedEffect
        stage.timer = { left.value }
        val total = (6_000 + 3_000 * expected).toLong()
        left.animateTo(0f, tween(((left.value) * total).toInt(), easing = LinearEasing))
        check(byClock = true)
    }
    LaunchedEffect(outcome) {
        val o = outcome ?: return@LaunchedEffect
        delay(if (o == Outcome.WON) 400 else 900)
        onResult(RoundResult(o, if (o == Outcome.WON) 0 else 1, 0, spec.targets, if (o == Outcome.LOST) spec.targets else emptyList(), timedOut = timedOut))
    }

    fun key(action: () -> Unit) { if (paused || outcome != null) return; action(); text = ime.text }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            spec.mode == TypingMode.COPY || (spec.mode == TypingMode.FROM_MEMORY && !hidden) ->
                Text(spec.item.korean, fontSize = 30.sp, fontWeight = FontWeight.Bold, style = JustStudyTheme.korean.koHero, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            spec.mode == TypingMode.FROM_MEMORY -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("?", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = c.brand)
                Text(spec.item.meaning, style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
            else -> Text(spec.item.meaning, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
        // The writing line: a 44 dp gap per expected block, underlined; one block more than expected shows in wrong.
        val typed = text.filter { it != ' ' }
        FlowRow(
            Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Typed: ${text.ifEmpty { "nothing" }}" },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            for (i in 0 until maxOf(expected, typed.length).coerceAtMost(expected + 1)) {
                val ch = typed.getOrNull(i)
                val line = when { outcome == Outcome.WON -> c.correctDeco; outcome == Outcome.LOST -> c.wrongBorder; i == typed.length -> c.brand; else -> c.field }
                Box(
                    Modifier.widthIn(min = 44.dp).height(48.dp).drawBehind { drawLine(line, Offset(0f, size.height - 1.5.dp.toPx()), Offset(size.width, size.height - 1.5.dp.toPx()), 3.dp.toPx()) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (ch != null) Text(ch.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (i >= expected) c.wrong else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (outcome == Outcome.LOST) Text(spec.item.korean, style = JustStudyTheme.korean.koPrompt, color = c.correct)
        HangulKeyboard(
            onJamo = { j -> key { ime.input(j) } }, onSpace = { key { ime.space() } }, onDelete = { key { ime.delete() } },
            onCheck = { if (!paused) check() }, checkEnabled = text.isNotBlank() && outcome == null, enabled = !paused && outcome == null,
        )
        Box(Modifier.width(1.dp))
    }
}
