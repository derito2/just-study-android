package com.dero.juststudykorean.ui.games.minigames

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dero.juststudykorean.korean.games.Evaluators
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.games.pieces.Tile
import com.dero.juststudykorean.ui.games.pieces.TileState
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.delay

/** The bins' Korean names (games-minigames.md 8). */
private val BIN_KO = mapOf(
    "colors" to "색깔", "family" to "가족", "animals" to "동물", "body" to "몸", "food" to "음식", "drinks" to "음료", "weather" to "날씨",
    "clothes" to "옷", "numbers" to "숫자", "days" to "날", "emotions" to "감정", "transportation" to "교통", "jobs" to "직업", "house" to "집",
    "school" to "학교", "Verbs" to "동사", "Adjectives" to "형용사", "Nouns" to "명사",
)

/**
 * Word Sort (games-minigames.md 8): drag each word to its bin, or tap the word and then the bin. Ends when all are
 * placed: won with 2 mistakes or fewer. A wrong drop goes back to the tray and counts against that word.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordSortGame(spec: RoundSpec.WordSort, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope) {
    LaunchedEffect(spec) { stage.clear() }
    val c = JustStudyTheme.colors
    val placed = remember { mutableStateMapOf<Int, Int>() } // tile → bin
    val missed = remember { mutableStateMapOf<Int, Int>() } // tile → misses
    var selected by remember { mutableStateOf<Int?>(null) }
    var wrong by remember { mutableStateOf<Int?>(null) }
    var wrongBin by remember { mutableStateOf<Int?>(null) }
    var shake by remember { mutableIntStateOf(0) }
    val bins = remember { mutableStateMapOf<Int, Rect>() }
    val done = placed.size == spec.tiles.size

    fun drop(tile: Int, bin: Int) {
        if (paused || done || tile in placed) return
        selected = null
        if (Evaluators.sort(spec, tile, bin)) placed[tile] = bin
        else { missed[tile] = (missed[tile] ?: 0) + 1; wrong = tile; wrongBin = bin; shake++ }
    }
    LaunchedEffect(shake) { if (shake > 0) { delay(600); wrong = null; wrongBin = null } }
    LaunchedEffect(done) {
        if (!done) return@LaunchedEffect
        delay(700)
        val mistakes = missed.values.sum()
        onResult(RoundResult(if (mistakes <= 2) Outcome.WON else Outcome.LOST, mistakes, 0, spec.targets, missed.keys.map { spec.tiles[it].first.key }))
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.bins.forEachIndexed { b, label ->
                val inside = placed.filterValues { it == b }.keys.sorted()
                val total = spec.tiles.count { it.second == b }
                val border = when { b == wrongBin -> c.wrongBorder; done -> c.correctDeco; selected != null -> c.brand; else -> c.line }
                Column(
                    Modifier.weight(1f).height(132.dp)
                        .background(c.canvas, RoundedCornerShape(16.dp))
                        .drawBehind {
                            val dash = if (done || selected != null) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                            drawRoundRect(border, cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = dash))
                        }
                        .onGloballyPositioned { bins[b] = it.boundsInRoot() }
                        .selectable(false, enabled = selected != null && !paused, role = Role.Button) { selected?.let { drop(it, b) } }
                        .semantics { contentDescription = "$label, ${inside.size} of $total" }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1)
                        Text(BIN_KO[label] ?: "", fontSize = 11.sp, color = c.muted, maxLines = 1)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        inside.forEach { t ->
                            val item = spec.tiles[t].first
                            Text(
                                if (done) "${item.korean} · ${item.meaning}" else item.korean, fontSize = 12.sp, maxLines = 1,
                                modifier = Modifier.background(c.tint, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            spec.tiles.forEachIndexed { t, (item, _) ->
                if (t in placed) return@forEachIndexed
                var drag by remember { mutableStateOf(Offset.Zero) }
                var home by remember { mutableStateOf(Rect.Zero) }
                Column(
                    Modifier
                        .zIndex(if (drag != Offset.Zero) 1f else 0f)
                        .onGloballyPositioned { home = it.boundsInRoot() }
                        .graphicsLayer { translationX = drag.x; translationY = drag.y; if (drag != Offset.Zero) { scaleX = 1.06f; scaleY = 1.06f } }
                        .pointerInput(t, paused) {
                            if (paused) return@pointerInput
                            detectDragGestures(
                                onDrag = { change, amount -> change.consume(); drag += amount },
                                onDragEnd = {
                                    val at = home.center + drag
                                    bins.entries.firstOrNull { it.value.contains(at) }?.let { drop(t, it.key) }
                                    drag = Offset.Zero
                                },
                                onDragCancel = { drag = Offset.Zero },
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val state = when { t == wrong -> TileState.Wrong; t == selected -> TileState.Selected; else -> TileState.Normal }
                    Tile(item.korean, { if (!paused) selected = if (selected == t) null else t }, state = state, shake = if (t == wrong) shake else 0, textStyle = JustStudyTheme.korean.koTitle, maxLines = 1, description = item.korean)
                    if (spec.showMeaning) Text(item.meaning, fontSize = 11.sp, color = c.muted, maxLines = 1)
                }
            }
        }
    }
}
