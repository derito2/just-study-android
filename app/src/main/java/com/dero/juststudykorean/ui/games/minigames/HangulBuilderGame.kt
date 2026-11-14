package com.dero.juststudykorean.ui.games.minigames

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import com.dero.juststudykorean.korean.games.Evaluators
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.hangul.VOWELS
import com.dero.juststudykorean.korean.hangul.decompose
import com.dero.juststudykorean.ui.games.pieces.BlockTile
import com.dero.juststudykorean.ui.games.pieces.Slot
import com.dero.juststudykorean.ui.games.pieces.SyllableFrame
import com.dero.juststudykorean.ui.games.pieces.TileState
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.delay

private const val TRIES = 3

/**
 * Hangul Builder (games-minigames.md 7): build the missing block of a word in its SyllableFrame. A jamo goes to the
 * first free cell of its kind (consonant: initial, then final; vowel: vowel); a full cell gives it back. Checked when
 * every cell is full: wrong pieces go back; the third wrong try loses and the block builds itself.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HangulBuilderGame(spec: RoundSpec.HangulBuilder, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope) {
    LaunchedEffect(spec) { stage.clear() }
    val block = decompose(spec.item.blocks[spec.missing].single())!!
    val vowelCells = spec.pieces.size - 1 - block.tail.length // 2 when a compound vowel is two pieces (level 3)
    val cells = remember { mutableStateListOf<Int?>().apply { repeat(spec.pieces.size) { add(null) } } } // tray index per cell
    var wrongTries by remember { mutableIntStateOf(0) }
    var shake by remember { mutableIntStateOf(0) }
    var outcome by remember { mutableStateOf<Outcome?>(null) }

    fun isVowelCell(i: Int) = i in 1..vowelCells

    fun check() {
        val placed = cells.map { spec.tray[it!!] }
        if (Evaluators.builder(spec, placed)) { outcome = Outcome.WON; return }
        wrongTries++; shake++
        if (wrongTries >= TRIES) { outcome = Outcome.LOST; return }
        for (i in cells.indices) if (placed[i] != spec.pieces[i]) cells[i] = null
    }

    fun tapTray(t: Int) {
        if (paused || outcome != null || t in cells) return
        val vowel = spec.tray[t] in VOWELS
        val free = cells.indices.firstOrNull { cells[it] == null && isVowelCell(it) == vowel } ?: return
        cells[free] = t
        if (cells.none { it == null }) check()
    }

    LaunchedEffect(outcome) {
        val o = outcome ?: return@LaunchedEffect
        delay(600)
        onResult(RoundResult(o, wrongTries, 0, spec.targets, if (o == Outcome.LOST) spec.targets else emptyList()))
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.item.blocks.forEachIndexed { i, b ->
                when {
                    i != spec.missing -> BlockTile(b, null, state = if (outcome == Outcome.WON) TileState.Correct else TileState.Normal)
                    outcome != null -> BlockTile(b, null, state = if (outcome == Outcome.WON) TileState.Correct else TileState.Wrong)
                    else -> Box(contentAlignment = Alignment.Center) { Slot(i, spec.item.blocks.size, highlight = true); Text("?", style = JustStudyTheme.korean.koPrompt) }
                }
            }
        }
        val shown = if (outcome == Outcome.LOST) spec.pieces else cells.map { it?.let(spec.tray::get) }
        SyllableFrame(block.vowel, vowelCells, block.tail.length, shown, 96.dp, onCell = { i -> if (!paused && outcome == null) cells[i] = null })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.tray.forEachIndexed { t, jamo ->
                BlockTile(jamo.toString(), { tapTray(t) }, state = if (t in cells) TileState.Used else TileState.Normal, shake = if (t !in cells) 0 else shake)
            }
        }
    }
}
