package com.dero.juststudykorean.ui.games.minigames

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.korean.games.Evaluators
import com.dero.juststudykorean.korean.games.JamoMark
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.games.wordleJamo
import com.dero.juststudykorean.korean.hangul.HangulIme
import com.dero.juststudykorean.korean.hangul.jamoOf
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.Pill
import com.dero.juststudykorean.ui.components.PillTone
import com.dero.juststudykorean.ui.games.pieces.HangulKeyboard
import com.dero.juststudykorean.ui.games.pieces.JAMO_NAMES
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.delay

private val CELL = RoundedCornerShape(8.dp)

/**
 * Hangul Wordle (design/beta.md 7, F7): the word's jamo in a grid of [RoundSpec.HangulWordle.attempts] rows; each guess is
 * whole syllables. In place: correct fill with ✓; elsewhere: warn fill with •; not in it: canvas with a line border.
 * [initial]: guesses already made (screenshot tests).
 */
@Composable
fun HangulWordleGame(spec: RoundSpec.HangulWordle, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope, initial: List<String> = emptyList()) {
    val n = spec.jamo.size
    val guesses = remember { mutableStateListOf<List<Char>>().apply { addAll(initial.mapNotNull { wordleJamo(it, n) }) } }
    val ime = remember { HangulIme() }
    var text by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf<Outcome?>(null) }
    var said by remember { mutableStateOf("") }
    LaunchedEffect(spec) { stage.clear() }

    fun check() {
        val g = wordleJamo(text, n) ?: return
        guesses += g
        said = Evaluators.wordle(spec.jamo, g).joinToString(", ") { it.name.lowercase() }
        while (ime.text.isNotEmpty()) ime.delete()
        text = ""
        if (Evaluators.wordleWon(spec.jamo, g)) outcome = Outcome.WON else if (guesses.size >= spec.attempts) outcome = Outcome.LOST
    }
    LaunchedEffect(outcome) {
        val o = outcome ?: return@LaunchedEffect
        delay(if (o == Outcome.WON) 500 else 900)
        onResult(RoundResult(o, guesses.size - if (o == Outcome.WON) 1 else 0, 0, spec.targets, if (o == Outcome.LOST) spec.targets else emptyList()))
    }
    fun key(action: () -> Unit) { if (paused || outcome != null) return; action(); text = ime.text }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        spec.firstSyllable?.let { Pill("Starts with $it", PillTone.Info) }
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = said }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val typing = text.filter { it in '가'..'힣' }.flatMap { jamoOf(it) }
            for (r in 0 until spec.attempts) {
                val guess = guesses.getOrNull(r)
                val marks = guess?.let { Evaluators.wordle(spec.jamo, it) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until n) JamoCell(guess?.get(i) ?: typing.getOrNull(i).takeIf { r == guesses.size }, marks?.get(i))
                }
            }
        }
        if (outcome == Outcome.LOST) Text(spec.item.korean, style = JustStudyTheme.korean.koPrompt, color = JustStudyTheme.colors.correctInk)
        HangulKeyboard(
            onJamo = { j -> key { ime.input(j) } }, onSpace = {}, onDelete = { key { ime.delete() } },
            onCheck = { if (!paused) check() }, checkEnabled = wordleJamo(text, n) != null && outcome == null, enabled = !paused && outcome == null,
        )
    }
}

@Composable
private fun JamoCell(jamo: Char?, mark: JamoMark?) {
    val c = JustStudyTheme.colors
    val fill = when (mark) { JamoMark.Hit -> c.correctFill; JamoMark.Present -> c.warnFill; else -> c.canvas }
    val border = when (mark) { JamoMark.Hit -> c.correctDeco; JamoMark.Present -> c.warn; else -> c.line }
    val name = jamo?.let { JAMO_NAMES[it] ?: it.toString() }
    Box(
        Modifier.size(44.dp).background(fill, CELL).border(2.dp, border, CELL)
            .clearAndSetSemantics { contentDescription = listOfNotNull(name, mark?.let { when (it) { JamoMark.Hit -> "in place"; JamoMark.Present -> "elsewhere"; JamoMark.Miss -> "not in the word" } }).joinToString(", ") },
        contentAlignment = Alignment.Center,
    ) {
        if (jamo != null) Text(jamo.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (mark == JamoMark.Hit || mark == JamoMark.Present) c.fillInk else MaterialTheme.colorScheme.onSurface)
        // Never by colour alone: ✓ in place, • elsewhere.
        when (mark) {
            JamoMark.Hit -> LucideIcon(Lucide.Check, contentDescription = null, size = 12.dp, tint = c.correctInk, modifier = Modifier.align(Alignment.TopEnd).offset((-2).dp, 2.dp))
            JamoMark.Present -> Box(Modifier.align(Alignment.TopEnd).offset((-5).dp, 5.dp).size(6.dp).background(c.fillWarn, CircleShape))
            else -> Unit
        }
    }
}
