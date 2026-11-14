package com.dero.juststudykorean.ui.games.minigames

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.korean.games.Evaluators
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.games.pieces.ReplayButton
import com.dero.juststudykorean.ui.games.pieces.Restartable
import com.dero.juststudykorean.ui.games.pieces.Tile
import com.dero.juststudykorean.ui.games.pieces.TileState
import com.dero.juststudykorean.ui.games.pieces.VoiceBars
import com.dero.juststudykorean.ui.games.pieces.VolumeUpPill
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REPLAYS = 2

/**
 * Listen & Guess (games-minigames.md 6): the targets sound one after the other over the same cards; tap the one you
 * hear. A second miss points the right card out. Won when at least 2 (of 3, or 2 of 2) came on the first try.
 */
@Composable
fun ListenGuessGame(spec: RoundSpec.ListenGuess, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope) {
    LaunchedEffect(spec) { stage.clear() }
    Restartable(paused) { speaker ->
        val scope = rememberCoroutineScope()
        var target by remember { mutableIntStateOf(0) }
        var tries by remember { mutableIntStateOf(0) }
        var replays by remember { mutableIntStateOf(REPLAYS) }
        var firstTry by remember { mutableIntStateOf(0) }
        var wrong by remember { mutableStateOf<Int?>(null) }
        var shake by remember { mutableIntStateOf(0) }
        var soundedFor by remember { mutableIntStateOf(-1) }
        val solved = remember { mutableStateListOf<Int>() }
        val pointed = remember { mutableStateListOf<Int>() }
        val failed = remember { mutableStateListOf<String>() }
        val done = target >= spec.played.size

        LaunchedEffect(target, paused) {
            if (paused || done || soundedFor == target) return@LaunchedEffect
            delay(if (target == 0) 400 else 700)
            soundedFor = target
            replays = REPLAYS
            speaker.play(listOf(spec.played[target].korean))
        }
        LaunchedEffect(shake) { if (shake > 0) { delay(600); wrong = null } }
        LaunchedEffect(done) {
            if (!done) return@LaunchedEffect
            delay(700)
            val need = minOf(2, spec.played.size)
            onResult(RoundResult(if (firstTry >= need) Outcome.WON else Outcome.LOST, failed.size, 0, spec.targets, failed.toList()))
        }

        fun next() { tries = 0; target++ }
        fun tap(card: Int) {
            if (paused || done || card in solved || card in pointed) return
            if (Evaluators.listen(spec, target, spec.cards[card])) {
                if (tries == 0) firstTry++
                solved += card; next()
            } else {
                tries++; wrong = card; shake++
                if (tries >= 2) { failed += spec.played[target].key; pointed += spec.cards.indexOfFirst { it.key == spec.played[target].key }; next() }
            }
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            VolumeUpPill(speaker)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${minOf(target + 1, spec.played.size)} / ${spec.played.size}", style = MaterialTheme.typography.labelMedium, color = JustStudyTheme.colors.muted, modifier = Modifier.width(48.dp))
                ReplayButton(speaker.speaking, replays, REPLAYS, onClick = {
                    if (!done && replays > 0) { replays--; scope.launch { speaker.play(listOf(spec.played[target].korean)) } }
                }, enabled = !paused && !done)
                VoiceBars(speaker.speaking, modifier = Modifier.width(48.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                spec.cards.chunked(2).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { k, item ->
                            val i = r * 2 + k
                            val shown = if (spec.showKorean) item.korean else item.meaning
                            val other = if (spec.showKorean) item.meaning else item.korean
                            val state = when { i in solved -> TileState.Correct; i in pointed || i == wrong -> TileState.Wrong; else -> TileState.Normal }
                            Tile(
                                if (i in solved || i in pointed) "$shown\n$other" else shown, { tap(i) }, Modifier.width(170.dp),
                                korean = spec.showKorean, state = state, shake = if (i == wrong) shake else 0, minHeight = 72.dp, maxLines = 3,
                            )
                        }
                    }
                }
            }
        }
    }
}
