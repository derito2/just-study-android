package com.dero.juststudykorean.ui.games.minigames

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.korean.games.Evaluators
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.games.pieces.Slot
import com.dero.juststudykorean.ui.games.pieces.Tile
import com.dero.juststudykorean.ui.games.pieces.TileState
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.delay

/**
 * Sentence Builder (games-minigames.md 3): the translation, a line of slots, the shuffled tiles. Tap a tile to place
 * it in the first free slot, tap a placed one to take it back; Check when the line is full. The same last word in
 * another order is neutral (Korean allows several orders the app can't check).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceBuilderGame(spec: RoundSpec.SentenceBuilder, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope) {
    LaunchedEffect(spec) { stage.clear() }
    val c = JustStudyTheme.colors
    val n = spec.answer.size
    val line = remember { mutableStateListOf<Int?>().apply { repeat(n) { add(null) } } }
    var outcome by remember { mutableStateOf<Outcome?>(null) }
    var shake by remember { mutableIntStateOf(0) }

    fun place(tile: Int) {
        if (paused || outcome != null || tile in line) return
        line.indexOfFirst { it == null }.takeIf { it >= 0 }?.let { line[it] = tile }
    }

    fun back(slot: Int) { if (!paused && outcome == null) line[slot] = null }

    fun check() {
        val order = line.map { spec.tiles[it!!] }
        val o = Evaluators.sentence(spec, order)
        if (o == Outcome.LOST) shake++
        outcome = o
    }

    LaunchedEffect(outcome) {
        val o = outcome ?: return@LaunchedEffect
        delay(600)
        onResult(RoundResult(o, if (o == Outcome.LOST) 1 else 0, 0, spec.targets, if (o == Outcome.LOST) spec.targets else emptyList()))
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            spec.item.meaning, Modifier.fillMaxWidth().background(c.canvas, RoundedCornerShape(12.dp)).padding(12.dp),
            fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).border(2.dp, c.line, RoundedCornerShape(12.dp)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            line.forEachIndexed { i, tile ->
                if (tile == null) Slot(i, n, width = 64.dp, height = 48.dp) else {
                    val word = spec.tiles[tile]
                    val state = when (outcome) {
                        Outcome.WON -> TileState.Correct
                        Outcome.LOST -> if (word == spec.answer[i]) TileState.Correct else TileState.Wrong
                        else -> TileState.Normal
                    }
                    Tile(word, { back(i) }, state = state, shake = if (state == TileState.Wrong) shake else 0)
                }
            }
            if (spec.sign.isNotEmpty()) Text(spec.sign, style = JustStudyTheme.korean.koTitle, color = if (outcome == Outcome.WON) c.correct else MaterialTheme.colorScheme.onSurface)
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.tiles.forEachIndexed { t, word -> Tile(word, { place(t) }, state = if (t in line) TileState.Used else TileState.Normal) }
        }
        StickerButton("Check", ::check, Modifier.fillMaxWidth(), ko = "확인", color = StickerColor.Action, enabled = line.none { it == null } && outcome == null && !paused)
    }
}
