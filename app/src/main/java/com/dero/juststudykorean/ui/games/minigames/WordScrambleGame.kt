package com.dero.juststudykorean.ui.games.minigames

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.games.pieces.BlockTile
import com.dero.juststudykorean.ui.games.pieces.Slot
import com.dero.juststudykorean.ui.games.pieces.TileState
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.delay

private const val TRIES = 3

/**
 * Word Scramble (games-minigames.md 5): tap a block to send it to the first free slot, tap a placed one to take it
 * back. Checked when the last slot fills: right blocks stay (locked), wrong ones go back; the third wrong try loses.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordScrambleGame(spec: RoundSpec.WordScramble, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope) {
    LaunchedEffect(spec) { stage.clear() }
    val c = JustStudyTheme.colors
    val n = spec.item.blocks.size
    val slots = remember { mutableStateListOf<Int?>().apply { repeat(n) { add(null) } } } // tile index in each slot
    val locked = remember { mutableStateListOf<Int>() } // slot indexes right after a check
    var wrongTries by remember { mutableIntStateOf(0) }
    var shake by remember { mutableIntStateOf(0) }
    var outcome by remember { mutableStateOf<Outcome?>(null) }

    fun check() {
        val placed = slots.map { spec.tiles[it!!] }
        if (placed == spec.item.blocks) { outcome = Outcome.WON; return }
        wrongTries++
        shake++
        if (wrongTries >= TRIES) { outcome = Outcome.LOST; return }
        for (i in 0 until n) if (placed[i] == spec.item.blocks[i]) locked += i else slots[i] = null
    }

    fun tapTray(tile: Int) {
        if (paused || outcome != null || tile in slots) return
        val free = slots.indexOfFirst { it == null }.takeIf { it >= 0 } ?: return
        slots[free] = tile
        if (slots.none { it == null }) check()
    }

    fun tapSlot(slot: Int) {
        if (paused || outcome != null || slot in locked) return
        slots[slot] = null
    }

    LaunchedEffect(outcome) {
        val o = outcome ?: return@LaunchedEffect
        delay(if (o == Outcome.WON) 400 else 600)
        onResult(RoundResult(o, wrongTries, 0, spec.targets, if (o == Outcome.LOST) spec.targets else emptyList()))
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until n) {
                val tile = slots[i]
                val shown = if (outcome == Outcome.LOST) spec.item.blocks[i] else tile?.let { spec.tiles[it] }
                if (shown == null) Slot(i, n) else BlockTile(
                    shown, { tapSlot(i) }, shake = if (i !in locked) shake else 0,
                    state = when { outcome == Outcome.WON || i in locked -> TileState.Correct; outcome == Outcome.LOST -> TileState.Wrong; else -> TileState.Normal },
                )
            }
        }
        // Three dots: the tries left (6 dp, field; spent: wrong-border).
        Row(Modifier.semantics { contentDescription = "${TRIES - wrongTries} tries left" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(TRIES) { Box(Modifier.size(6.dp).background(if (it < wrongTries) c.wrongBorder else c.field, CircleShape)) }
        }
        if (outcome == Outcome.WON) Text(spec.item.meaning, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        Spacer(Modifier.height(32.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.tiles.forEachIndexed { t, block ->
                val used = t in slots || (outcome == Outcome.WON && block == spec.extra)
                BlockTile(block, { tapTray(t) }, state = if (used) TileState.Used else TileState.Normal)
            }
        }
    }
}
