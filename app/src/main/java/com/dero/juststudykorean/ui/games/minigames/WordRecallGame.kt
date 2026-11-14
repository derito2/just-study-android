package com.dero.juststudykorean.ui.games.minigames

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.korean.games.Evaluators
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.games.pieces.Tile
import com.dero.juststudykorean.ui.games.pieces.TileState
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.games.talkBackOn
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.delay

private enum class Phase { Show, Memorize, Rebuild, Over }

/**
 * Word Recall (games-minigames.md 10): show the words one by one (n × 700 ms), memorize them with the moon as clock
 * (n × 900 ms; with TalkBack, no limit and a Ready button), then tap them in order. Won with one mistake at most; the
 * second loses. A lantern per word on the scene. A pause while showing or memorizing replays the round's start (9).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordRecallGame(spec: RoundSpec.WordRecall, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope) {
    val c = JustStudyTheme.colors
    val n = spec.sequence.size
    val talkBack = talkBackOn()
    var phase by remember { mutableStateOf(Phase.Show) }
    var shown by remember { mutableIntStateOf(0) }
    var next by remember { mutableIntStateOf(0) }
    var mistakes by remember { mutableIntStateOf(0) }
    var wrongTile by remember { mutableStateOf<Int?>(null) }
    var start by remember { mutableIntStateOf(0) } // grows when a pause replays the start
    val failed = remember { mutableListOf<String>() }
    val left = remember { Animatable(1f) } // the moon's clock while memorizing

    LaunchedEffect(paused) { if (paused && (phase == Phase.Show || phase == Phase.Memorize)) { phase = Phase.Show; shown = 0; start++ } }
    LaunchedEffect(start, paused) {
        if (paused || phase != Phase.Show) return@LaunchedEffect
        stage.timer = null
        for (i in 1..n) { stage.lanterns = n to (i - 1); shown = i; stage.lanterns = n to i; delay(700) }
        phase = Phase.Memorize
        if (talkBack) return@LaunchedEffect
        left.snapTo(1f)
        stage.timer = { left.value }
        left.animateTo(0f, tween(n * 900, easing = LinearEasing))
        stage.timer = null
        phase = Phase.Rebuild
        stage.lanterns = n to 0
    }
    LaunchedEffect(wrongTile) { if (wrongTile != null) { delay(500); wrongTile = null } }
    LaunchedEffect(phase) {
        if (phase != Phase.Over) return@LaunchedEffect
        delay(600)
        onResult(RoundResult(if (mistakes <= 1) Outcome.WON else Outcome.LOST, mistakes, 0, spec.targets, failed.distinct()))
    }

    fun tap(tile: Int) {
        if (paused || phase != Phase.Rebuild) return
        val item = spec.tiles[tile]
        if (Evaluators.recall(spec, next, item)) {
            next++
            stage.lanterns = n to next
            if (next == n) phase = Phase.Over
        } else {
            mistakes++
            failed += spec.sequence[next].key
            wrongTile = tile
            if (mistakes >= 2) phase = Phase.Over
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (phase == Phase.Rebuild) Text("Tap them in the same order. · 같은 순서로 누르세요", style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.sequence.forEachIndexed { i, item ->
                val faceUp = when (phase) { Phase.Show -> i < shown; Phase.Memorize -> true; Phase.Rebuild -> i < next; Phase.Over -> true }
                val lost = phase == Phase.Over && mistakes >= 2 && i >= next
                Box(
                    Modifier.weight(1f).height(if (n == 5) 80.dp else 96.dp)
                        .background(if (faceUp) c.canvas else if (phase == Phase.Show) Color.Transparent else c.action, RoundedCornerShape(12.dp))
                        .border(2.dp, if (lost) c.wrongBorder else if (faceUp && i < next) c.correctDeco else c.line, RoundedCornerShape(12.dp))
                        .semantics(mergeDescendants = true) { contentDescription = if (faceUp) "${i + 1}: ${item.korean}" else "Card ${i + 1}, face down" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", Modifier.align(Alignment.TopCenter), style = MaterialTheme.typography.labelSmall, color = if (faceUp) c.muted else Color.White)
                    if (faceUp) Text(item.korean, style = if (n == 5) JustStudyTheme.korean.koBody.copy(fontWeight = FontWeight.Bold) else JustStudyTheme.korean.koTitle, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    else if (phase != Phase.Show) Text("${i + 1}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
        if (phase == Phase.Memorize && talkBack) StickerButton("Ready", { phase = Phase.Rebuild; stage.lanterns = n to 0 }, color = StickerColor.Action)
        if (phase == Phase.Rebuild || phase == Phase.Over) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                spec.tiles.forEachIndexed { t, item ->
                    val placed = spec.sequence.indexOfFirst { it.key == item.key } < next
                    Tile(item.korean, { tap(t) }, state = when { placed -> TileState.Used; wrongTile == t -> TileState.Wrong; else -> TileState.Normal }, shake = if (wrongTile == t) mistakes else 0)
                }
            }
        }
    }
}
