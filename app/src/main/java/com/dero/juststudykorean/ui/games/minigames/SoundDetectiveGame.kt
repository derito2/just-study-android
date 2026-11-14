package com.dero.juststudykorean.ui.games.minigames

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.korean.games.Evaluators
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec.SoundDetective
import com.dero.juststudykorean.ui.games.pieces.ReplayButton
import com.dero.juststudykorean.ui.games.pieces.Restartable
import com.dero.juststudykorean.ui.games.pieces.Slot
import com.dero.juststudykorean.ui.games.pieces.Speaker
import com.dero.juststudykorean.ui.games.pieces.Tile
import com.dero.juststudykorean.ui.games.pieces.TileState
import com.dero.juststudykorean.ui.games.pieces.VoiceBars
import com.dero.juststudykorean.ui.games.pieces.VolumeUpPill
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sound Detective (games-minigames.md 13): trains the ear, never grades speech. A: same word or different; B: which
 * came twice; C: the order heard. One replay of the whole sequence; it sounds by itself 400 ms after the pieces come.
 */
@Composable
fun SoundDetectiveGame(spec: SoundDetective, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope) {
    LaunchedEffect(spec) { stage.clear() }
    Restartable(paused) { speaker ->
        val texts = when (spec) {
            is SoundDetective.SameOrDifferent -> listOf(spec.first.korean, spec.second.korean)
            is SoundDetective.CameTwice -> spec.played.map { spec.words[it].korean }
            is SoundDetective.WhatOrder -> spec.order.map { it.korean }
        }
        val scope = rememberCoroutineScope()
        var sounded by remember { mutableStateOf(false) }
        var replays by remember { mutableIntStateOf(1) }
        var outcome by remember { mutableStateOf<Outcome?>(null) }
        LaunchedEffect(paused) {
            if (paused || sounded) return@LaunchedEffect
            delay(400); sounded = true
            speaker.play(texts, spec.rate, spec.pauseMs)
        }
        LaunchedEffect(outcome) {
            val o = outcome ?: return@LaunchedEffect
            delay(1600)
            onResult(RoundResult(o, if (o == Outcome.WON) 0 else 1, 0, spec.targets, if (o == Outcome.LOST) spec.targets else emptyList()))
        }
        val canAnswer = !paused && outcome == null && sounded && !speaker.speaking
        fun answer(won: Boolean) { if (canAnswer) outcome = if (won) Outcome.WON else Outcome.LOST }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This trains your ear. It doesn't grade how you speak.", fontSize = 12.sp, color = JustStudyTheme.colors.muted, textAlign = TextAlign.Center)
            VolumeUpPill(speaker)
            when (spec) {
                is SoundDetective.SameOrDifferent -> SameOrDifferent(spec, speaker, outcome != null, canAnswer, ::answer)
                is SoundDetective.CameTwice -> CameTwice(spec, speaker, outcome, canAnswer, ::answer)
                is SoundDetective.WhatOrder -> WhatOrder(spec, speaker, outcome, canAnswer, ::answer)
            }
            ReplayButton(speaker.speaking, replays, 1, onClick = {
                if (replays > 0) { replays--; scope.launch { speaker.play(texts, spec.rate, spec.pauseMs) } }
            }, enabled = !paused && outcome == null && sounded)
        }
    }
}

@Composable
private fun SameOrDifferent(spec: SoundDetective.SameOrDifferent, speaker: Speaker, answered: Boolean, canAnswer: Boolean, answer: (Boolean) -> Unit) {
    val c = JustStudyTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        listOf(spec.first, spec.second).forEachIndexed { i, w ->
            val on = speaker.current == i
            Box(
                Modifier.size(96.dp).graphicsLayer { if (on) { scaleX = 1.06f; scaleY = 1.06f } }
                    .background(c.tint, CircleShape).border(2.dp, c.tintBorder, CircleShape)
                    .semantics { contentDescription = if (answered) "${w.korean}, ${w.meaning}" else "Sound ${i + 1}" },
                contentAlignment = Alignment.Center,
            ) {
                if (answered) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(w.korean, style = JustStudyTheme.korean.koPrompt, color = MaterialTheme.colorScheme.onSurface)
                    Text(w.meaning, fontSize = 11.sp, color = c.muted, maxLines = 1)
                } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${i + 1}", fontWeight = FontWeight.Bold, color = c.brandText)
                    VoiceBars(on)
                }
            }
        }
    }
    if (answered) Text(if (spec.same) "Same word." else "Different: only ${spec.explanation ?: "one sound"} changes.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile("Same\n같아요", { answer(Evaluators.sameOrDifferent(spec, true)) }, Modifier.width(160.dp), minHeight = 64.dp, maxLines = 2, description = "Same")
        Tile("Different\n달라요", { answer(Evaluators.sameOrDifferent(spec, false)) }, Modifier.width(160.dp), minHeight = 64.dp, maxLines = 2, description = "Different")
    }
}

/** Four (or more) dots, one per audio: the one sounding lights up; after answering, each shows its word. */
@Composable
private fun SoundDots(count: Int, speaker: Speaker, words: List<String>?) {
    val c = JustStudyTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(count) { i ->
            val on = speaker.current == i
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(40.dp).graphicsLayer { if (on) { scaleX = 1.15f; scaleY = 1.15f } }
                        .background(if (on) c.brand else c.canvas, CircleShape).border(2.dp, c.line, CircleShape)
                        .semantics { contentDescription = "Sound ${i + 1}" },
                    contentAlignment = Alignment.Center,
                ) { if (on) VoiceBars(true, Color.White, Modifier.size(20.dp)) else Text("${i + 1}", fontSize = 12.sp, color = c.muted) }
                words?.getOrNull(i)?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CameTwice(spec: SoundDetective.CameTwice, speaker: Speaker, outcome: Outcome?, canAnswer: Boolean, answer: (Boolean) -> Unit) {
    var picked by remember { mutableStateOf<Int?>(null) }
    SoundDots(spec.played.size, speaker, if (outcome != null) spec.played.map { spec.words[it].korean } else null)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        spec.words.forEachIndexed { i, w ->
            val state = when { outcome == null -> TileState.Normal; i == spec.twice -> TileState.Correct; i == picked -> TileState.Wrong; else -> TileState.Normal }
            Tile("${w.korean}\n${w.meaning}", { if (canAnswer) { picked = i; answer(Evaluators.cameTwice(spec, i)) } }, korean = true, state = state, minHeight = 64.dp, maxLines = 2, description = w.korean)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhatOrder(spec: SoundDetective.WhatOrder, speaker: Speaker, outcome: Outcome?, canAnswer: Boolean, answer: (Boolean) -> Unit) {
    val placed = remember { mutableStateListOf<Int>() } // tile indices in slot order
    SoundDots(spec.order.size, speaker, if (outcome != null) spec.order.map { it.korean } else null)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(spec.order.size) { s ->
            val t = placed.getOrNull(s)
            if (t == null) Slot(s, spec.order.size, width = 76.dp)
            else {
                val right = spec.tiles[t].key == spec.order[s].key
                Tile(spec.tiles[t].korean, { if (outcome == null) placed.removeAt(s) }, korean = true, minWidth = 76.dp, state = if (outcome == null) TileState.Normal else if (right) TileState.Correct else TileState.Wrong, maxLines = 1)
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        spec.tiles.forEachIndexed { i, w ->
            Tile(w.korean, {
                if (canAnswer && i !in placed) {
                    placed += i
                    if (placed.size == spec.order.size) answer(Evaluators.order(spec, placed.map { spec.tiles[it] }))
                }
            }, korean = true, state = if (i in placed) TileState.Used else TileState.Normal, maxLines = 1)
        }
    }
}
