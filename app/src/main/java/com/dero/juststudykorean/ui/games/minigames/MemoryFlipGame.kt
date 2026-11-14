package com.dero.juststudykorean.ui.games.minigames

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.korean.games.Evaluators
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.reduceMotion
import kotlinx.coroutines.delay

private const val FAILED_AFTER = 3 // a word with 3 or more failed attempts is noted as failed

/**
 * Memory Flip (games-minigames.md 4): cards face down; turn two, a pair stays, else they turn back after 1000 ms
 * (level 3: 700). A tap on any card closes them sooner (seen at least 300 ms). Always won when complete.
 */
@Composable
fun MemoryFlipGame(spec: RoundSpec.MemoryFlip, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope) {
    LaunchedEffect(spec) { stage.clear() }
    val open = remember { mutableStateListOf<Int>() } // cards turned and waiting
    val matched = remember { mutableStateListOf<Int>() } // card indexes
    val misses = remember { mutableStateMapOf<Int, Int>() } // item → failed attempts
    var attempts by remember { mutableIntStateOf(0) }
    var shownAt by remember { mutableLongStateOf(0L) }
    var done by remember { mutableStateOf(false) }
    val big = LocalDensity.current.fontScale >= 1.3f
    val columns = if (big) 3 else spec.columns

    fun close() { open.clear() }

    fun tap(card: Int) {
        if (paused || done || card in matched || card in open) return
        if (open.size == 2) { if (System.nanoTime() - shownAt >= 300_000_000L) close() else return }
        open += card
        if (open.size == 2) {
            val (a, b) = open[0] to open[1]
            if (Evaluators.memoryPair(spec, a, b)) {
                matched += listOf(a, b); open.clear()
            } else {
                attempts++
                listOf(a, b).map { spec.cards[it].first }.distinct().forEach { misses[it] = (misses[it] ?: 0) + 1 }
                shownAt = System.nanoTime()
            }
        }
    }

    LaunchedEffect(shownAt) { if (shownAt != 0L && open.size == 2) { delay(spec.shownMs.toLong()); if (open.size == 2) close() } }
    LaunchedEffect(matched.size) {
        if (matched.size == spec.cards.size && !done) {
            done = true
            delay(400)
            val failed = misses.filterValues { it >= FAILED_AFTER }.keys.map { spec.items[it].key }
            onResult(RoundResult(Outcome.WON, attempts, 0, spec.targets, failed))
        }
    }

    val (w, h) = if (columns == 3) 118.dp to 96.dp else 88.dp to 88.dp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        spec.cards.indices.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { i ->
                    val (item, meaningSide) = spec.cards[i]
                    MemoryCard(
                        i, spec.items[item].let { if (meaningSide) it.meaning else it.korean }, meaningSide,
                        faceUp = i in open || i in matched || done, matched = i in matched,
                        modifier = Modifier.size(w, h), onClick = { tap(i) },
                    )
                }
            }
        }
    }
}

/** A card: the back (action, 한) or a face (Korean: tint, 한; meaning: canvas, EN); the turn is a rotation in Y. */
@Composable
private fun MemoryCard(index: Int, text: String, meaning: Boolean, faceUp: Boolean, matched: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = JustStudyTheme.colors
    val still = LocalFrozenMotion.current || reduceMotion()
    val turn = remember { Animatable(if (faceUp) 180f else 0f) }
    LaunchedEffect(faceUp) { val target = if (faceUp) 180f else 0f; if (still) turn.snapTo(target) else turn.animateTo(target, tween(Motion.Slow, easing = Motion.EaseInOut)) }
    val shape = RoundedCornerShape(12.dp)
    val density = LocalDensity.current.density
    val label = when { matched -> "$text, matched"; faceUp -> if (meaning) "$text, English" else "$text, Korean"; else -> "Card ${index + 1}, face down" }
    Box(
        modifier
            .graphicsLayer { rotationY = turn.value; cameraDistance = 12f * density }
            .alpha(if (matched) 0.55f else 1f)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label; role = Role.Button },
    ) {
        // Both sides exist; the draw phase shows the back before 90° and the face after (nothing recomposes per frame).
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (turn.value <= 90f) 1f else 0f }.background(c.action, shape), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().padding(6.dp).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp)))
            Text("한", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.25f))
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer { rotationY = 180f; alpha = if (turn.value > 90f) 1f else 0f }
                .background(if (meaning) c.canvas else c.tint, shape)
                .border(2.dp, if (matched) c.correctDeco else if (meaning) c.line else c.tintBorder, shape)
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (meaning) "EN" else "한", Modifier.align(Alignment.TopStart), fontSize = 10.sp, color = if (meaning) c.muted else c.brandText)
            if (matched) LucideIcon(Lucide.Check, contentDescription = null, size = 14.dp, tint = c.correct, modifier = Modifier.align(Alignment.TopEnd))
            Text(
                text, style = if (meaning) JustStudyTheme.korean.bodyBold.copy(fontSize = 13.sp) else JustStudyTheme.korean.koTitle,
                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
