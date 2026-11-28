package com.dero.juststudykorean.ui.games.minigames

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.korean.games.Evaluators
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RUNNER_FORMS
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.components.OptionState
import com.dero.juststudykorean.ui.components.QuizOption
import com.dero.juststudykorean.ui.games.stage.StageEvent
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.dero.juststudykorean.ui.theme.reduceMotion
import kotlinx.coroutines.delay

private const val STILL_SECONDS = 5

/**
 * Conjugation Runner (design/beta.md 7, F7): the verb and the ending on top; 3 or 4 forms fall as wide QuizOptions and
 * you tap the right one before they reach the bottom. With "Remove animations" they don't fall: a 5 s counter. Below,
 * {n}/10. Won with 8 of 10. The fall is drawn in the layer (no recomposition per frame).
 */
@Composable
fun ConjugationRunnerGame(spec: RoundSpec.ConjugationRunner, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope) {
    val c = JustStudyTheme.colors
    val still = LocalFrozenMotion.current || reduceMotion()
    var index by remember(spec) { mutableIntStateOf(0) }
    var right by remember(spec) { mutableIntStateOf(0) }
    val failed = remember(spec) { mutableStateListOf<String>() }
    var chosen by remember(spec) { mutableStateOf<String?>(null) }
    var missed by remember(spec) { mutableStateOf(false) }
    var left by remember(spec) { mutableIntStateOf(STILL_SECONDS) }
    val fall = remember(spec) { Animatable(0f) }
    var travel by remember { mutableIntStateOf(0) }
    val started = remember(spec) { System.currentTimeMillis() }
    val q = spec.questions.getOrNull(index)
    val answered = chosen != null || missed
    LaunchedEffect(spec) { stage.clear() }

    fun choose(option: String) {
        if (paused || answered || q == null) return
        chosen = option
        if (option == q.answer) { right++; stage.send(StageEvent.PieceCorrect) } else failed += q.item.key
    }
    // The fall (or the counter); it stops while paused and goes on from where it was.
    LaunchedEffect(index, paused, answered) {
        if (paused || answered || q == null) return@LaunchedEffect
        if (still) {
            while (left > 0) { delay(1000); left-- }
        } else {
            fall.animateTo(1f, tween(((1f - fall.value) * spec.fallMs).toInt().coerceAtLeast(1), easing = LinearEasing))
        }
        missed = true
        failed += q.item.key
    }
    LaunchedEffect(index, answered) {
        if (!answered || q == null) return@LaunchedEffect
        delay(if (chosen == q.answer) 400 else 900)
        if (index + 1 >= spec.questions.size) {
            val won = Evaluators.runnerWon(right)
            onResult(RoundResult(if (won) Outcome.WON else Outcome.LOST, spec.questions.size - right, System.currentTimeMillis() - started, spec.targets, failed.distinct()))
        } else {
            index++; chosen = null; missed = false; left = STILL_SECONDS; fall.snapTo(0f)
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (q != null) {
            Text("${q.item.korean} · ${q.ending.label}", style = JustStudyTheme.korean.koPrompt, color = MaterialTheme.colorScheme.onSurface)
            Text(q.item.meaning, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Box(Modifier.fillMaxWidth().height(if (still) 220.dp else 280.dp).clipToBounds().onSizeChanged { travel = it.height }) {
            if (q != null) Column(
                Modifier.fillMaxWidth()
                    .graphicsLayer { translationY = if (still) 0f else fall.value * (travel - size.height).coerceAtLeast(0f) },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                q.options.forEachIndexed { i, o ->
                    val state = when {
                        !answered -> OptionState.Open
                        o == q.answer -> OptionState.Correct
                        o == chosen -> OptionState.Chosen
                        else -> OptionState.Other
                    }
                    QuizOption(i + 1, o, state, { choose(o) })
                }
            }
        }
        if (still && !answered) Text("$left s", style = JustStudyTheme.korean.bodyBold, color = c.muted)
        Text(
            "${minOf(index + 1, RUNNER_FORMS)}/${spec.questions.size}", style = MaterialTheme.typography.bodySmall, color = c.muted,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "${index + 1} of ${spec.questions.size}, $right right" },
        )
    }
}
