package com.dero.juststudykorean.ui.games.minigames

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.korean.games.Evaluators
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.games.pieces.Tile
import com.dero.juststudykorean.ui.games.pieces.TileState
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.delay

/**
 * Grammar Fixer (games-minigames.md 9): tap the word with the wrong particle (a second miss points it out), then pick
 * the fix. Won when found in one or two tries and fixed on the first; it always ends with the sentence corrected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrammarFixerGame(spec: RoundSpec.GrammarFixer, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope) {
    LaunchedEffect(spec) { stage.clear() }
    val c = JustStudyTheme.colors
    var misses by remember { mutableIntStateOf(0) }
    var missed by remember { mutableStateOf<Int?>(null) }
    var shake by remember { mutableIntStateOf(0) }
    var found by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<String?>(null) }
    val fixed = picked != null
    val badParticle = spec.words[spec.wrong].removePrefix(spec.noun)

    fun tapWord(i: Int) {
        if (paused || found) return
        if (Evaluators.fixerWord(spec, i)) { found = true; return }
        misses++; missed = i; shake++
        if (misses >= 2) found = true
    }
    LaunchedEffect(picked) {
        val p = picked ?: return@LaunchedEffect
        val won = misses < 2 && Evaluators.fixerFix(spec, p)
        delay(if (won) 1200 else 1700)
        onResult(RoundResult(if (won) Outcome.WON else Outcome.LOST, misses + if (Evaluators.fixerFix(spec, p)) 0 else 1, 0, spec.targets, if (won) emptyList() else spec.targets))
    }

    Column(Modifier.fillMaxWidth().semantics { contentDescription = spec.words.joinToString(" ") + spec.sign }, verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            spec.words.forEachIndexed { i, w ->
                if (i == spec.wrong && found) {
                    // Split: stem | particle (wrong until fixed).
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tile(spec.noun, null, state = if (fixed) TileState.Correct else TileState.Normal, textStyle = JustStudyTheme.korean.koPrompt, maxLines = 1)
                        val good = picked?.let { Evaluators.fixerFix(spec, it) } == true && misses < 2
                        Tile(if (fixed) spec.correct else badParticle, null, state = when { !fixed -> TileState.Wrong; good -> TileState.Correct; else -> TileState.Wrong }, pop = if (fixed) 1 else 0, textStyle = JustStudyTheme.korean.koPrompt, maxLines = 1, description = if (fixed) spec.correct else "$badParticle, wrong particle")
                    }
                } else {
                    Tile(w, { tapWord(i) }, state = if (i == missed && !found) TileState.Wrong else TileState.Normal, shake = if (i == missed) shake else 0, textStyle = JustStudyTheme.korean.koPrompt, maxLines = 1)
                }
            }
            Text(spec.sign, style = JustStudyTheme.korean.koPrompt, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(spec.item.meaning, style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center)
        if (found && !fixed) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                spec.options.forEach { o -> Tile(o, { if (!paused) picked = o }, textStyle = JustStudyTheme.korean.koPrompt, minWidth = 64.dp, maxLines = 1) }
            }
        }
        if (fixed) {
            Text(
                spec.rule, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.background(c.canvas, RoundedCornerShape(12.dp)).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
