package com.dero.juststudykorean.ui.games.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.Tokens
import com.dero.juststudykorean.ui.theme.reduceMotion
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** The scene's height under the status bar (design/games.md 4); the board covers it from [BOARD_TOP]. */
const val STAGE_DP = 168f
const val BOARD_TOP_DP = 144f
private const val W = 411f

/** What a minigame draws on the scene (games.md 4.1, 4.2): the moon's clock and the lanterns. */
@Stable
class StageScope {
    /** The clock: the fraction of time left (1 → 0), read in the draw phase; null without a clock. */
    var timer: (() -> Float)? by mutableStateOf(null)
    /** The lanterns: [total] hang from the pagoda's eaves, the first [lit] are lit; null without lanterns. */
    var lanterns: Pair<Int, Int>? by mutableStateOf(null)

    fun clear() { timer = null; lanterns = null }
}

private class Star(val x: Float, val y: Float, val r: Float, val alpha: Float, val period: Float, val phase: Float)

// 14 fixed stars (seed 7), y < 90 (games.md 4).
private val STARS = Random(7).let { r -> List(14) { Star(r.nextFloat() * W, 6f + r.nextFloat() * 84f, 0.5f + r.nextFloat() * 0.5f, 0.5f + r.nextFloat() * 0.4f, 2.4f + r.nextFloat() * 1.6f, r.nextFloat()) } }

private class Petal(val start: Float, val duration: Float, val dx: Float, val dy: Float, val turn: Float, val phase: Float)

private val PETALS = Random(11).let { r -> List(12) { Petal(it * 0.04f, 900f + r.nextFloat() * 400f, 260f + r.nextFloat() * 120f, 120f + r.nextFloat() * 60f, 180f + r.nextFloat() * 180f, r.nextFloat() * 6f) } }

/**
 * The night scene (games.md 4): one Canvas, nothing recomposes per frame. [parallaxKey]: when it changes (a new game),
 * the mountains slide and come back; [burst]: when it grows, 12 petals cross the scene (7.3). [verticalScale] > 1 fills
 * a taller area (the opening, 8).
 */
@Composable
fun GameStage(scope: StageScope, parallaxKey: Any?, burst: Int, modifier: Modifier = Modifier, verticalScale: () -> Float = { 1f }) {
    val still = LocalFrozenMotion.current || reduceMotion()
    val clock: State<Float> = if (still) remember { mutableStateOf(0f) } else
        rememberInfiniteTransition(label = "stars").animateFloat(0f, 1f, infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val pulse: State<Float> = if (still) remember { mutableStateOf(0f) } else
        rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "p")
    val parallax = remember { Animatable(0f) }
    val petals = remember { Animatable(1f) }
    LaunchedEffect(parallaxKey) {
        if (still || parallaxKey == null) return@LaunchedEffect
        parallax.animateTo(1f, tween(Motion.Base, easing = Motion.EaseInOut))
        parallax.animateTo(0f, tween(Motion.Base, easing = Motion.EaseInOut))
    }
    LaunchedEffect(burst) {
        if (still || burst == 0) return@LaunchedEffect
        petals.snapTo(0f)
        petals.animateTo(1f, tween(1300, easing = LinearEasing))
    }
    Box(
        modifier.clearAndSetSemantics { }.drawWithCache {
            val u = size.width / W // dp of the design → px
            val sy = verticalScale()
            fun y(v: Float) = v * u * sy
            fun ridge(peaks: List<Pair<Float, Float>>, bottom: Float, shift: Float): Path = Path().apply {
                moveTo(-20f * u + shift, y(bottom))
                val pts = listOf(-20f to peaks.first().second) + peaks + listOf(W + 20f to peaks.last().second)
                lineTo(pts.first().first * u + shift, y(pts.first().second))
                for (i in 1 until pts.size) {
                    val (x0, y0) = pts[i - 1]
                    val (x1, y1) = pts[i]
                    val mx = (x0 + x1) / 2
                    quadraticTo(x0 * u + shift, y(y0), mx * u + shift, y((y0 + y1) / 2))
                    if (i == pts.lastIndex) lineTo(x1 * u + shift, y(y1))
                }
                lineTo((W + 20f) * u + shift, y(bottom))
                close()
            }
            val sky = Brush.verticalGradient(0f to Tokens.NightDeep, (70f / STAGE_DP) to Tokens.Night, (150f / STAGE_DP) to Tokens.Action, 1f to Tokens.Action)
            onDrawBehind {
                val shift = parallax.value
                drawRect(sky, size = Size(size.width, size.height))
                // Stars: each breathes ±0.25 with its own period (the only endless loop of Games).
                for (s in STARS) {
                    val a = (s.alpha + 0.25f * sin(2 * PI * (clock.value * 40f / s.period + s.phase)).toFloat()).coerceIn(0f, 1f)
                    drawCircle(Color.White.copy(alpha = a), s.r * u, Offset(s.x * u, y(s.y)))
                }
                drawMoon(u, ::y, scope.timer, pulse.value)
                drawPath(ridge(listOf(60f to 96f, 170f to 84f, 300f to 100f), STAGE_DP, -6f * u * shift), Tokens.SkyMid.copy(alpha = 0.35f))
                drawPath(ridge(listOf(0f to 118f, 120f to 104f, 250f to 112f, 411f to 98f), STAGE_DP, -12f * u * shift), Tokens.NightShade)
                translate(left = -20f * u * shift) {
                    drawPagoda(u, ::y)
                    scope.lanterns?.let { (total, lit) -> drawLanterns(u, ::y, total, lit) } // between the middle and near mountains
                }
                drawPath(ridge(listOf(0f to 140f, 90f to 130f, 330f to 138f, 411f to 126f), STAGE_DP * 4, -20f * u * shift), Tokens.NightDeep)
                if (petals.value < 1f) drawPetals(u, petals.value)
            }
        },
    )
}

private fun DrawScope.drawMoon(u: Float, y: (Float) -> Float, timer: (() -> Float)?, pulse: Float) {
    val center = Offset(322f * u, y(66f))
    val left = timer?.invoke()
    val beat = if (left != null && left < 0.25f && left > 0f) 1f + 0.06f * sin(PI * pulse).toFloat() else 1f
    drawCircle(Brush.radialGradient(listOf(Tokens.SkyLight.copy(alpha = 0.25f), Color.Transparent), center, 42f * u), 42f * u, center)
    drawCircle(Tokens.SkyLight, 22f * u * beat, center)
    if (left != null && left > 0f) { // 4.1: a ring of 3 dp, radius 29 dp, consumed clockwise from 12
        val ring = 29f * u
        val topLeft = Offset(center.x - ring, center.y - ring)
        val stroke = Stroke(3f * u, cap = StrokeCap.Round)
        drawArc(Tokens.SkyMid.copy(alpha = 0.4f), -90f, 360f, false, topLeft, Size(ring * 2, ring * 2), style = stroke)
        drawArc(if (left < 0.25f) Tokens.Lantern else Color.White, -90f + 360f * (1 - left), 360f * left, false, topLeft, Size(ring * 2, ring * 2), style = stroke)
    }
}

private fun DrawScope.drawPagoda(u: Float, y: (Float) -> Float) {
    val x = 46f * u
    val base = y(132f)
    val w = 56f * u
    val h = 46f * u
    val roof = Path().apply { // two curved roofs, as splash_flash1
        moveTo(x - 6f * u, base - h * 0.45f); quadraticTo(x + w / 2, base - h * 0.62f, x + w + 6f * u, base - h * 0.45f)
        lineTo(x + w * 0.8f, base - h * 0.6f); lineTo(x + w * 0.2f, base - h * 0.6f); close()
        moveTo(x - 2f * u, base - h * 0.9f); quadraticTo(x + w / 2, base - h * 1.05f, x + w + 2f * u, base - h * 0.9f)
        lineTo(x + w * 0.72f, base - h * 1.02f); lineTo(x + w * 0.28f, base - h * 1.02f); close()
    }
    drawRect(Tokens.NightDeep, Offset(x + w * 0.18f, base - h * 0.45f), Size(w * 0.64f, h * 0.45f))
    drawRect(Tokens.NightDeep, Offset(x + w * 0.26f, base - h * 0.9f), Size(w * 0.48f, h * 0.32f))
    drawPath(roof, Tokens.NightDeep)
}

/** 4.2: a string of lanterns from the pagoda's eave, spread between x = 110 and x = 250; lit ones glow, the rest are dark. */
private fun DrawScope.drawLanterns(u: Float, y: (Float) -> Float, total: Int, lit: Int) {
    val rope = Path().apply { moveTo(96f * u, y(90f)); quadraticTo(180f * u, y(102f), 264f * u, y(88f)) }
    drawPath(rope, Tokens.SkyMid.copy(alpha = 0.6f), style = Stroke(1f * u))
    for (i in 0 until total) {
        val x = if (total == 1) 180f else 110f + 140f * i / (total - 1)
        val t = (x - 96f) / 168f
        val ropeY = (1 - t) * (1 - t) * 90f + 2 * (1 - t) * t * 102f + t * t * 88f // the quadratic's y at that x (close enough)
        val cx = x * u
        val top = y(ropeY)
        val on = i < lit
        drawLine(Tokens.SkyMid.copy(alpha = 0.6f), Offset(cx, top), Offset(cx, top + 4f * u), 1f * u)
        if (on) drawCircle(Tokens.Lantern.copy(alpha = 0.3f), 14f * u, Offset(cx, top + 12f * u))
        drawRoundRect(if (on) Tokens.LanternRed else Tokens.SkyMid.copy(alpha = 0.45f), Offset(cx - 6f * u, top + 4f * u), Size(12f * u, 16f * u), CornerRadius(4f * u))
        if (on) drawRoundRect(Tokens.Lantern, Offset(cx - 3f * u, top + 7f * u), Size(6f * u, 10f * u), CornerRadius(2f * u))
    }
}

/** 7.3: 12 petals from the top right corner down to the left, a sine of ±14 dp, fading in their last 30 %. */
private fun DrawScope.drawPetals(u: Float, t: Float) {
    for (p in PETALS) {
        val local = ((t * 1300f - p.start * 1300f) / p.duration).coerceIn(0f, 1f)
        if (local <= 0f || local >= 1f) continue
        val x = (W - 10f - p.dx * local) * u + 14f * u * sin(2 * PI * local + p.phase).toFloat()
        val yy = (8f + p.dy * local) * u
        val alpha = if (local > 0.7f) (1f - local) / 0.3f else 1f
        rotate(p.turn * local, Offset(x, yy)) { drawOval(Tokens.Petal.copy(alpha = alpha), Offset(x - 3f * u, yy - 5f * u), Size(6f * u, 10f * u)) }
    }
}
