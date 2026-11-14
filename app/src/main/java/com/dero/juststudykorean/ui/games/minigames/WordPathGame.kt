package com.dero.juststudykorean.ui.games.minigames

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.korean.games.Evaluators
import com.dero.juststudykorean.korean.games.Evaluators.PathCheck
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.games.LocalGameBank
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val TRIES = 3

/**
 * Korean Word Path (games-minigames.md 12): trace the word through the grid, 8 directions, no cell twice. Drag and
 * release to check, or tap neighbouring cells and the last one again. Going back to the previous cell undoes a step.
 * Another word of your notes costs no try; a third wrong path loses and lights the right one.
 */
@Composable
fun WordPathGame(spec: RoundSpec.WordPath, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope) {
    LaunchedEffect(spec) { stage.clear() }
    val c = JustStudyTheme.colors
    val bank = LocalGameBank.current
    val path = remember { mutableStateListOf<Int>() }
    var tries by remember { mutableIntStateOf(TRIES) }
    var outcome by remember { mutableStateOf<Outcome?>(null) }
    var other by remember { mutableStateOf<String?>(null) }
    var flash by remember { mutableIntStateOf(0) }
    val n = spec.size
    val cellDp = if (n == 3) 80.dp else 76.dp
    val gapDp = 12.dp

    fun adjacent(a: Int, b: Int) = a != b && abs(a / n - b / n) <= 1 && abs(a % n - b % n) <= 1

    fun step(cell: Int) {
        when {
            path.isEmpty() -> path += cell
            path.size >= 2 && cell == path[path.size - 2] -> path.removeAt(path.lastIndex)
            cell !in path && adjacent(path.last(), cell) -> path += cell
        }
    }

    fun check() {
        if (path.size < 2) { path.clear(); return }
        when (val r = Evaluators.path(spec, path.toList(), bank)) {
            PathCheck.Target -> outcome = Outcome.WON
            is PathCheck.OtherWord -> { other = r.item.meaning; flash++; path.clear() }
            PathCheck.Wrong -> { tries--; path.clear(); if (tries == 0) outcome = Outcome.LOST }
        }
    }

    fun tap(cell: Int) {
        if (paused || outcome != null) return
        if (path.isNotEmpty() && cell == path.last()) check() else step(cell)
    }

    LaunchedEffect(flash) { if (flash > 0) { delay(1200); other = null } }
    LaunchedEffect(outcome) {
        val o = outcome ?: return@LaunchedEffect
        delay(if (o == Outcome.WON) 700 else 1200)
        onResult(RoundResult(o, TRIES - tries, 0, spec.targets, if (o == Outcome.LOST) spec.targets else emptyList()))
    }

    val shown = if (outcome == Outcome.LOST) spec.path else path.toList()
    val lineColor = when (outcome) { Outcome.WON -> c.correctDeco; Outcome.LOST -> c.wrongBorder; else -> c.brand.copy(alpha = 0.85f) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
            Text(shown.joinToString("") { spec.grid[it] }, style = JustStudyTheme.korean.koTitle, color = c.brandText)
        }
        val side = cellDp * n + gapDp * (n - 1)
        Box(
            Modifier.size(side)
                .drawBehind {
                    val cell = cellDp.toPx(); val gap = gapDp.toPx()
                    fun center(i: Int) = Offset((i % n) * (cell + gap) + cell / 2, (i / n) * (cell + gap) + cell / 2)
                    shown.zipWithNext().forEach { (a, b) -> drawLine(lineColor, center(a), center(b), 10.dp.toPx(), StrokeCap.Round) }
                }
                .pointerInput(spec, paused, outcome) {
                    if (paused || outcome != null) return@pointerInput
                    val cell = cellDp.toPx(); val gap = gapDp.toPx()
                    // A cell counts only near its centre, so a diagonal move doesn't catch the corner of a neighbour.
                    fun at(p: Offset): Int? {
                        val col = (p.x / (cell + gap)).toInt(); val row = (p.y / (cell + gap)).toInt()
                        if (col !in 0 until n || row !in 0 until n) return null
                        val centre = Offset(col * (cell + gap) + cell / 2, row * (cell + gap) + cell / 2)
                        return if ((p - centre).getDistance() <= cell * 0.42f) row * n + col else null
                    }
                    detectDragGestures(
                        onDragStart = { p -> path.clear(); at(p)?.let { path += it } },
                        onDrag = { change, _ -> change.consume(); at(change.position)?.let { if (path.isEmpty() || it != path.last()) step(it) } },
                        onDragEnd = { check() },
                        onDragCancel = { path.clear() },
                    )
                },
        ) {
            spec.grid.forEachIndexed { i, block ->
                val order = shown.indexOf(i)
                val inPath = order >= 0
                val dim = outcome == Outcome.WON && !inPath
                Box(
                    Modifier
                        .offset(x = (cellDp + gapDp) * (i % n), y = (cellDp + gapDp) * (i / n))
                        .size(cellDp)
                        .background(if (inPath) c.tint else c.canvas, CircleShape)
                        .border(2.dp, if (inPath) lineColor else c.line, CircleShape)
                        .selectable(false, enabled = !paused && outcome == null, role = Role.Button) { tap(i) }
                        .semantics { contentDescription = "$block, row ${i / n + 1}, column ${i % n + 1}" + if (inPath) ", in path, step ${order + 1}" else "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(block, style = JustStudyTheme.korean.koPrompt, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dim) 0.3f else 1f))
                    if (inPath) Text("${order + 1}", fontSize = 10.sp, color = c.brandText, modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics { contentDescription = "$tries tries left" }) {
            repeat(TRIES) { Box(Modifier.size(10.dp).background(if (it < tries) c.brand else c.field, CircleShape)) }
        }
        other?.let { Text("That's a word from your notes ($it), but not this one.", style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center) }
    }
}
