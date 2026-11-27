package com.dero.juststudykorean.ui.games.minigames

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.korean.games.CrossEntry
import com.dero.juststudykorean.korean.games.Evaluators
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.hangul.HangulIme
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.components.StickerSize
import com.dero.juststudykorean.ui.games.LocalSpeakWord
import com.dero.juststudykorean.ui.games.pieces.HangulKeyboard
import com.dero.juststudykorean.ui.games.pieces.LocalKoreanVoice
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_MISTAKES = 3
private val CELL = 44.dp

/**
 * Crossword (design/beta.md 7, F7): a grid of syllables drawn in ONE Canvas (never a Box per cell), 44 dp cells up to
 * 7 × 7. Closed: the grid and every clue. Tapping a cell or a clue opens the Hangul keys with only that word's clue
 * above, its Check and Done (owner, PR 10); Done, a full word or a tap outside closes them. A full word is checked:
 * right, it turns green and sounds (D10-3); wrong, its cells empty again. Three wrong words lose. [initial]: test state.
 */
@Composable
fun CrosswordGame(spec: RoundSpec.Crossword, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope, initial: Map<Int, String> = emptyMap(), startOpen: Boolean = false) {
    val c = JustStudyTheme.colors
    val solution = remember(spec) { spec.solution() }
    val filled = remember { mutableStateMapOf<Int, String>().apply { spec.givens.forEach { put(it, solution.getValue(it)) }; putAll(initial) } }
    val done = remember { mutableStateMapOf<CrossEntry, Boolean>() }
    var entry by remember { mutableStateOf(spec.entries.first()) }
    var cell by remember { mutableIntStateOf(spec.entries.first().cells(spec.cols).first { it !in spec.givens }) }
    val ime = remember { HangulIme() }
    var text by remember { mutableStateOf("") }
    var mistakes by remember { mutableIntStateOf(0) }
    var outcome by remember { mutableStateOf<Outcome?>(null) }
    var said by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(startOpen) }
    val voice = LocalKoreanVoice.current
    val speak = LocalSpeakWord.current.on
    val scope = rememberCoroutineScope()
    LaunchedEffect(spec) { stage.clear() }
    LaunchedEffect(mistakes) { stage.lanterns = MAX_MISTAKES to (MAX_MISTAKES - mistakes) }

    fun locked(i: Int) = i in spec.givens || done.keys.any { i in it.cells(spec.cols) }
    fun clearIme() { while (ime.text.isNotEmpty()) ime.delete(); text = "" }
    fun close() { clearIme(); open = false }
    fun select(e: CrossEntry, at: Int? = null) {
        entry = e; cell = at ?: e.cells(spec.cols).firstOrNull { it !in filled } ?: e.cells(spec.cols).first()
        if (!paused && outcome == null) open = true
    }
    fun place() {
        val syllable = text.lastOrNull { it in '가'..'힣' }?.toString() ?: return
        clearIme()
        if (locked(cell)) return
        filled[cell] = syllable
        val complete = spec.entries.filter { cell in it.cells(spec.cols) && it !in done && it.cells(spec.cols).all { k -> k in filled } }
        if (complete.isNotEmpty()) open = false // a full word closes the keys
        for (e in complete) {
            if (Evaluators.crossWord(e, spec, filled)) {
                done[e] = true
                said = "${e.number}: right"
                if (speak) voice?.let { v -> scope.launch { runCatching { v.speak(e.item.korean) } } }
            } else {
                mistakes++
                said = "${e.number}: not quite"
                e.cells(spec.cols).filter { !locked(it) }.forEach { filled.remove(it) }
            }
        }
        when {
            Evaluators.crossSolved(spec, filled) -> outcome = Outcome.WON
            mistakes >= MAX_MISTAKES -> outcome = Outcome.LOST
            else -> entry.cells(spec.cols).firstOrNull { it !in filled && !locked(it) }?.let { cell = it }
        }
    }
    LaunchedEffect(outcome) {
        val o = outcome ?: return@LaunchedEffect
        delay(if (o == Outcome.WON) 700 else 900)
        val failed = spec.entries.filter { it !in done }.map { it.item.key }
        onResult(RoundResult(o, mistakes, 0, spec.targets, if (o == Outcome.LOST) failed else emptyList()))
    }
    fun key(action: () -> Unit) { if (paused || outcome != null) return; action(); text = ime.text }

    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val shown = if (outcome == Outcome.LOST) solution else filled
    val editing = open && outcome == null && !paused
    // A tap outside the grid, its clues and the keys closes the keys.
    Column(
        Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { close() } },
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier.size(CELL * spec.cols, CELL * spec.rows)
                .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Crossword, ${spec.entries.size} words. $said" }
                .pointerInput(spec) {
                    detectTapGestures { p ->
                        val i = (p.y / CELL.toPx()).toInt() * spec.cols + (p.x / CELL.toPx()).toInt()
                        if (i !in solution) { close(); return@detectTapGestures }
                        if (paused || outcome != null) return@detectTapGestures
                        val mine = spec.entries.filter { i in it.cells(spec.cols) }
                        select(if (i == cell && open && mine.size > 1) mine.first { it != entry } else mine.firstOrNull { it == entry } ?: mine.first(), i)
                    }
                },
        ) {
            val s = CELL.toPx()
            val gap = 2.dp.toPx()
            for ((i, _) in solution) {
                val at = Offset((i % spec.cols) * s + gap, (i / spec.cols) * s + gap)
                val size = Size(s - 2 * gap, s - 2 * gap)
                val right = done.keys.any { i in it.cells(spec.cols) }
                val fill = when { right -> c.correctFill; editing && i == cell -> c.tint; editing && i in entry.cells(spec.cols) -> c.canvas; else -> c.surface }
                drawRoundRect(fill, at, size, CornerRadius(6.dp.toPx()))
                drawRoundRect(if (editing && i == cell) c.brand else if (right) c.correctDeco else c.line, at, size, CornerRadius(6.dp.toPx()), style = Stroke(2.dp.toPx()))
                spec.entries.firstOrNull { it.cells(spec.cols).first() == i }?.let { e ->
                    drawText(measurer, "${e.number}", at + Offset(3.dp.toPx(), 1.dp.toPx()), TextStyle(fontSize = 9.sp, color = c.muted, fontWeight = FontWeight.Bold))
                }
                val syllable = if (editing && i == cell && text.isNotEmpty()) text.last().toString() else shown[i]
                if (syllable != null) {
                    val t = measurer.measure(syllable, TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (right) c.fillInk else if (outcome == Outcome.LOST && i !in filled) c.wrongInk else ink))
                    drawText(t, topLeft = at + Offset((size.width - t.size.width) / 2, (size.height - t.size.height) / 2))
                }
            }
        }
        if (editing) {
            // Open: only the chosen word's clue, with its Check and Done, above the keys (no space row: a crossword has no spaces).
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${entry.number}. ${entry.item.meaning}", Modifier.weight(1f), style = JustStudyTheme.korean.bodyBold, color = c.brand, maxLines = 2)
                StickerButton("Done", { close() }, color = StickerColor.Gray, size = StickerSize.Small)
                StickerButton("Check", { if (!paused && outcome == null) place() }, color = StickerColor.Go, size = StickerSize.Small, enabled = text.any { it in '가'..'힣' })
            }
            HangulKeyboard(
                onJamo = { j -> key { ime.input(j) } }, onSpace = {}, onDelete = { key { ime.delete() } },
                onCheck = { place() }, checkEnabled = false, enabled = true, bottomRow = false,
            )
        } else {
            // Closed: every clue, Across | Down side by side; tapping one opens the keys on its word.
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (across in listOf(true, false)) Column(Modifier.weight(1f)) {
                    Text(if (across) "Across" else "Down", style = JustStudyTheme.korean.bodyBold, color = ink)
                    spec.entries.filter { it.across == across }.forEach { e ->
                        Text(
                            "${e.number}. ${e.item.meaning}${if (e in done) " ✓" else ""}",
                            Modifier.fillMaxWidth().heightIn(min = 32.dp).clickable(role = Role.Button, onClickLabel = "Write this word") { if (outcome == null && !paused && e !in done) select(e) },
                            style = MaterialTheme.typography.bodySmall, color = if (e in done) c.correctInk else ink, maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
