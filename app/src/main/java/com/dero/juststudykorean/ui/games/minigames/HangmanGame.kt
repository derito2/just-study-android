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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.korean.games.Evaluators
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.hangul.LEADS
import com.dero.juststudykorean.korean.hangul.VOWELS
import com.dero.juststudykorean.korean.hangul.decompose
import com.dero.juststudykorean.ui.games.pieces.BlockTile
import com.dero.juststudykorean.ui.games.pieces.JAMO_NAMES
import com.dero.juststudykorean.ui.games.pieces.Slot
import com.dero.juststudykorean.ui.games.pieces.SyllableFrame
import com.dero.juststudykorean.ui.games.pieces.Tile
import com.dero.juststudykorean.ui.games.pieces.TileState
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.delay

private const val LIVES = 3

/** The full jamo keyboard of the jamo mode: 14 simple consonants, 5 doubles (+2 empty), 21 vowels; rows of 7. */
private val JAMO_ROWS: List<List<Char?>> = run {
    val simple = LEADS.filter { it !in "ㄲㄸㅃㅆㅉ" }.toList()
    val doubles = "ㄲㄸㅃㅆㅉ".toList() + listOf(null, null)
    (simple + doubles + VOWELS.toList()).chunked(7)
}

/**
 * Hangul Hangman (games-minigames.md 1): find the word before the three lanterns go out. Level 1: syllable keys;
 * 2: jamo (a key reveals every place with that exact jamo, initial or final); 3: jamo with a short phrase.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HangmanGame(spec: RoundSpec.Hangman, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope) {
    val c = JustStudyTheme.colors
    val revealed = remember { mutableStateListOf<Int>() }
    val used = remember { mutableStateListOf<String>() }
    var misses by remember { mutableIntStateOf(0) }
    var said by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf<Outcome?>(null) }
    LaunchedEffect(misses) { stage.timer = null; stage.lanterns = LIVES to (LIVES - misses) }

    fun press(key: String) {
        if (paused || outcome != null || key in used) return
        used += key
        val at = Evaluators.hangmanReveals(spec, key)
        if (at.isEmpty()) {
            misses++
            said = "$key: no. ${LIVES - misses} lanterns left"
            if (misses >= LIVES) outcome = Outcome.LOST
        } else {
            revealed += at
            said = "$key: yes, ${at.size} places"
            if (Evaluators.hangmanSolved(spec, revealed.toSet())) outcome = Outcome.WON
        }
    }

    LaunchedEffect(outcome) {
        val o = outcome ?: return@LaunchedEffect
        delay(600)
        onResult(RoundResult(o, misses, 0, spec.targets, if (o == Outcome.LOST) spec.targets else emptyList()))
    }

    val showAll = outcome == Outcome.LOST
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // The word: a slot (or a frame in jamo mode) per block; a space is a 16 dp gap.
        if (!spec.jamoMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                spec.answer.forEachIndexed { i, block ->
                    if (i in revealed || showAll) BlockTile(block, null, state = if (showAll && i !in revealed) TileState.Wrong else TileState.Correct)
                    else Slot(i, spec.answer.size)
                }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var at = 0
                for (ch in spec.item.korean.trim().trimEnd('.', '?', '!')) {
                    if (ch == ' ') { Spacer(Modifier.width(16.dp)); at++; continue }
                    val b = decompose(ch) ?: continue
                    val n = 2 + b.tail.length
                    val cells = (at until at + n).map { if (it in revealed || showAll) spec.answer[it].single() else null }
                    SyllableFrame(b.vowel, 1, b.tail.length, cells, 56.dp)
                    at += n
                }
            }
        }
        Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = said })
        if (!spec.jamoMode) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                spec.keys.chunked(if (spec.keys.size > 10) 6 else 5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            val state = when { key !in used -> TileState.Normal; spec.answer.contains(key) -> TileState.Correct; else -> TileState.Wrong }
                            Tile(key, { press(key) }, state = state, minWidth = if (spec.keys.size > 10) 56.dp else 64.dp, minHeight = 56.dp, textStyle = JustStudyTheme.korean.koPrompt, maxLines = 1)
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                JAMO_ROWS.forEachIndexed { r, row ->
                    val vowels = row.any { it != null && it in VOWELS }
                    Row(Modifier.then(if (vowels) Modifier.background(c.tint, RoundedCornerShape(8.dp)) else Modifier).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { j ->
                            if (j == null) { Spacer(Modifier.size(48.dp, 44.dp)); return@forEach }
                            val key = j.toString()
                            val state = when { key !in used -> TileState.Normal; spec.answer.contains(key) -> TileState.Correct; else -> TileState.Wrong }
                            Tile(key, { press(key) }, state = state, minWidth = 48.dp, minHeight = 44.dp, maxLines = 1, description = JAMO_NAMES[j] ?: key)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(1.dp))
    }
}
