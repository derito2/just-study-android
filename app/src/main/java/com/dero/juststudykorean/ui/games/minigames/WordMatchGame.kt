package com.dero.juststudykorean.ui.games.minigames

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.games.pieces.Tile
import com.dero.juststudykorean.ui.games.pieces.TileState
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.delay

private const val FADE_AFTER_MS = 400L
private const val WRONG_MS = 400L

/**
 * Word Match (games-minigames.md 2): two columns shuffled apart; tap one tile then one of the other side, or drag a
 * line from one to the other. Won with 2 mistakes or fewer; a mistake counts against both words involved.
 */
@Composable
fun WordMatchGame(spec: RoundSpec.WordMatch, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope) {
    LaunchedEffect(spec) { stage.clear() }
    val c = JustStudyTheme.colors
    val items = spec.items
    var left by remember { mutableStateOf<Int?>(null) } // a row of the Korean column
    var right by remember { mutableStateOf<Int?>(null) } // a row of the meaning column
    val matched = remember { mutableStateListOf<Int>() } // item indexes
    val faded = remember { mutableStateListOf<Int>() }
    val failed = remember { mutableStateListOf<String>() }
    var mistakes by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (left row, right row) while it shakes
    val pops = remember { mutableStateMapOf<Int, Int>() }
    val shakes = remember { mutableStateMapOf<Int, Int>() }
    val bounds = remember { mutableStateMapOf<Pair<Boolean, Int>, Rect>() } // (meaning side, row) → bounds in the board
    var drag by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) } // the box in the root: tiles' bounds are kept relative to it
    var done by remember { mutableStateOf(false) }

    fun item(meaningSide: Boolean, row: Int) = if (meaningSide) spec.meaningOrder[row] else spec.koreanOrder[row]

    fun attempt(l: Int, r: Int) {
        left = null; right = null
        val a = item(false, l)
        val b = item(true, r)
        if (a == b) {
            matched += a
            pops[a] = (pops[a] ?: 0) + 1
        } else {
            mistakes++
            failed += listOf(items[a].key, items[b].key)
            shakes[a] = (shakes[a] ?: 0) + 1; shakes[b + 1000] = (shakes[b + 1000] ?: 0) + 1
            wrong = l to r
        }
    }

    fun tap(meaningSide: Boolean, row: Int) {
        if (paused || done || item(meaningSide, row) in matched) return
        if (meaningSide) {
            right = if (right == row) null else row
        } else {
            left = if (left == row) null else row
        }
        val l = left
        val r = right
        if (l != null && r != null) attempt(l, r)
    }

    LaunchedEffect(wrong) { if (wrong != null) { delay(WRONG_MS); wrong = null } }
    LaunchedEffect(matched.size) {
        val last = matched.lastOrNull() ?: return@LaunchedEffect
        if (matched.size == items.size && !done) {
            done = true
            delay(240)
            onResult(RoundResult(if (mistakes <= 2) Outcome.WON else Outcome.LOST, mistakes, 0, spec.targets, failed.distinct()))
        } else {
            delay(FADE_AFTER_MS); faded += last
        }
    }

    fun anchor(meaningSide: Boolean, row: Int): Offset? = bounds[meaningSide to row]?.let { if (meaningSide) Offset(it.left, it.center.y) else Offset(it.right, it.center.y) }

    fun hit(at: Offset): Pair<Boolean, Int>? = bounds.entries.firstOrNull { it.value.contains(at) }?.key

    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(spec, paused) {
                if (paused) return@pointerInput
                var from: Pair<Boolean, Int>? = null
                detectDragGestures(
                    onDragStart = { at -> from = hit(at)?.takeIf { (side, row) -> item(side, row) !in matched }; from?.let { (s, r) -> anchor(s, r)?.let { a -> drag = a to at } } },
                    onDrag = { change, _ -> drag = drag?.let { it.first to change.position } },
                    onDragEnd = {
                        val start = from
                        val end = drag?.second?.let(::hit)
                        drag = null
                        if (start != null && end != null && start.first != end.first && item(end.first, end.second) !in matched) {
                            if (start.first) attempt(end.second, start.second) else attempt(start.second, end.second)
                        }
                    },
                    onDragCancel = { drag = null },
                )
            }
            .drawBehind {
                fun curve(a: Offset, b: Offset, color: Color, alpha: Float) {
                    val dx = (b.x - a.x) * 0.4f
                    val path = Path().apply { moveTo(a.x, a.y); cubicTo(a.x + dx, a.y, b.x - dx, b.y, b.x, b.y) }
                    drawPath(path, color.copy(alpha = alpha), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
                for (i in matched) {
                    val a = anchor(false, spec.koreanOrder.indexOf(i)) ?: continue
                    val b = anchor(true, spec.meaningOrder.indexOf(i)) ?: continue
                    curve(a, b, c.correctDeco, if (i in faded && matched.size < items.size) 0.45f else 1f)
                }
                wrong?.let { (l, r) -> val a = anchor(false, l); val b = anchor(true, r); if (a != null && b != null) curve(a, b, c.wrongBorder, 1f) }
                drag?.let { (a, b) -> curve(a, b, c.brand, 1f) }
            },
    ) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                spec.koreanOrder.forEachIndexed { row, i ->
                    val state = when { i in matched -> TileState.Correct; wrong?.first == row -> TileState.Wrong; left == row -> TileState.Selected; else -> TileState.Normal }
                    val description = if (i in matched) "${items[i].korean}, matched with ${items[i].meaning}" else "${items[i].korean}, not matched"
                    Tile(
                        items[i].korean, { tap(false, row) },
                        Modifier.fillMaxWidth().alpha(if (i in faded && matched.size < items.size) 0.45f else 1f)
                            .onGloballyPositioned { bounds[false to row] = it.boundsInRoot().translate(-origin) },
                        state = state, pop = pops[i] ?: 0, shake = shakes[i] ?: 0, minHeight = 56.dp, maxLines = 2, description = description,
                    )
                }
            }
            Spacer(Modifier.width(35.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                spec.meaningOrder.forEachIndexed { row, i ->
                    val state = when { i in matched -> TileState.Correct; wrong?.second == row -> TileState.Wrong; right == row -> TileState.Selected; else -> TileState.Normal }
                    Tile(
                        items[i].meaning, { tap(true, row) },
                        Modifier.fillMaxWidth().alpha(if (i in faded && matched.size < items.size) 0.45f else 1f)
                            .onGloballyPositioned { bounds[true to row] = it.boundsInRoot().translate(-origin) },
                        korean = false, state = state, pop = pops[i] ?: 0, shake = shakes[i + 1000] ?: 0, minHeight = 56.dp, maxLines = 2,
                    )
                }
            }
        }
    }
}
