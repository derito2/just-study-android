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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue as getValueState
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.withFrameNanos
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The scene's height under the status bar (design/games.md 4); the board covers it from [BOARD_TOP]. */
const val STAGE_DP = 196f
const val BOARD_TOP_DP = 172f
private const val W = 411f

/** What a minigame draws on the scene (games.md 4.1, 4.2): the moon's clock and the lanterns. */
@Stable
class StageScope {
    /** The clock: the fraction of time left (1 → 0), read in the draw phase; null without a clock. */
    var timer: (() -> Float)? by mutableStateOf(null)
    /** The lanterns: [total] hang from the pagoda's eaves, the first [lit] are lit; null without lanterns. */
    var lanterns: Pair<Int, Int>? by mutableStateOf(null)

    /** The result of the round, for the fixed poses (F2 animates the way there). */
    var mood: StageMood by mutableStateOf(StageMood.Idle)
    /** The opening is on: Beom and Deung are not on the scene yet (A8 brings Beom in). */
    var entering: Boolean by mutableStateOf(false)
    /** Deung's flame shows from a streak of 3. */
    var streak: Int by mutableIntStateOf(0)
    /** Dal's face when it is not the clock's: [DalFace.Sleepy] in the pause, [DalFace.Proud] in the summary. */
    var dal: DalFace? by mutableStateOf(null)

    val director = StageDirector()
    /** Set by [GameStage]: its clock in ms (0 when motion is off). */
    var clockMs: () -> Float = { 0f }

    /** The game tells the stage what happened. With motion off only the fixed pose changes (a single frame). */
    fun send(e: StageEvent) {
        mood = when (e) {
            StageEvent.RoundWon -> StageMood.Won
            StageEvent.RoundLost -> StageMood.Lost
            is StageEvent.SessionEnded -> if (e.rounds > 0 && e.won * 10 >= e.rounds * 6) StageMood.Won else StageMood.Idle
            else -> if (e == StageEvent.NextRound || e == StageEvent.Resumed) StageMood.Idle else mood
        }
        if (e == StageEvent.Entered) entering = false
        director.handle(e, clockMs(), beomIdle(clockMs() / 1000f))
    }

    fun clear() { timer = null; lanterns = null; mood = StageMood.Idle; streak = 0; dal = null }
}

enum class StageMood { Idle, Won, Lost }

private class Star(val x: Float, val y: Float, val r: Float, val alpha: Float, val period: Float, val phase: Float)

// 22 fixed stars (seed 7), y < 100, 1–2 dp, opacity 0.4–0.9 (games-characters.md 2, layer 3).
private val STARS = Random(7).let { r -> List(22) { Star(r.nextFloat() * W, 6f + r.nextFloat() * 94f, 0.5f + r.nextFloat() * 0.5f, 0.4f + r.nextFloat() * 0.5f, 2.4f + r.nextFloat() * 1.6f, r.nextFloat()) } }
private val GRASS = Random(3).let { r -> List(9) { Triple(78f + it * 19f + r.nextFloat() * 8f, 5f + r.nextFloat() * 3f, r.nextFloat()) } }

// A0 (games-characters.md 5.1): a blink every 3.2–5.5 s, an ear flick or a look every 9–14 s; fixed seeds, so every session plays the same.
private val BLINKS = Random(5).let { r -> var t = 2f; List(160) { t += 3.2f + r.nextFloat() * 2.3f; t } }
private val GESTURES = Random(9).let { r -> var t = 6f; List(80) { t += 9f + r.nextFloat() * 5f; t } }

/** The idle pose of Beom at [t] seconds (A0): breathing, the tail, a blink and now and then a gesture. */
internal fun beomIdle(t: Float): BeomPose {
    val k = 0.5f - 0.5f * cos(2 * PI * t / 2.4f).toFloat()
    val b = BLINKS.lastOrNull { it <= t }?.let { (t - it) / 0.12f }?.takeIf { it in 0f..1f }
    val g = GESTURES.indexOfLast { it <= t }.takeIf { it >= 0 }
    val gp = g?.let { t - GESTURES[it] } ?: 99f
    val flick = if (g != null && g % 2 == 0 && gp < 0.2f) 25f * sin(PI * gp / 0.2f).toFloat() else 0f
    val look = if (g != null && g % 2 == 1 && gp < 0.6f) 2f * sin(PI * gp / 0.6f).toFloat() else 0f
    return BeomPose(
        sx = 1f - 0.015f * k, sy = 1f + 0.03f * k, tail = 0.4f * sin(2 * PI * t / 1.8f).toFloat(),
        earR = flick, lookY = look, blink = if (b == null) 1f else 1f - 0.9f * (1f - abs(2f * b - 1f)),
    )
}

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
    // A0 and the fog read this in the draw phase only. It stops outside RESUMED (games-characters.md 8) and when motion is off.
    val seconds = remember { mutableFloatStateOf(0f) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val t0 = withFrameNanos { it } - (seconds.floatValue * 1e9f).toLong()
            while (true) withFrameNanos { seconds.floatValue = (it - t0) / 1e9f }
        }
    }
    scope.clockMs = { seconds.floatValue * 1000f }
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
            val sky = Brush.verticalGradient(0f to Tokens.NightDeep, (90f / STAGE_DP) to Tokens.Night, (184f / STAGE_DP) to Tokens.Action, 1f to Tokens.Action)
            val midFill = Brush.verticalGradient(listOf(Tokens.NightShade, Tokens.Night), startY = y(112f), endY = y(STAGE_DP))
            onDrawBehind {
                val shift = parallax.value
                val t = seconds.floatValue
                // 1 sky, 2 glow of the horizon
                drawRect(sky, size = Size(size.width, size.height))
                drawCircle(Brush.radialGradient(listOf(Tokens.SkyMid.copy(alpha = 0.28f), Color.Transparent), Offset(262f * u, y(150f)), 190f * u), 190f * u, Offset(262f * u, y(150f)))
                // 3 stars: each breathes ±0.25 with its own period; 3 sparkles
                for (s in STARS) {
                    val a = (s.alpha + 0.25f * sin(2 * PI * (clock.value * 40f / s.period + s.phase)).toFloat()).coerceIn(0f, 1f)
                    drawCircle(Color.White.copy(alpha = a), s.r * u, Offset(s.x * u, y(s.y)))
                }
                for ((sx, sy) in listOf(38f to 30f, 150f to 22f, 372f to 92f)) drawSparkle(Offset(sx * u, y(sy)), 6f * u)
                // 4 Dal
                drawMoon(u, ::y, scope.timer, pulse.value, scope.dal)
                // 5 far mountains, 6 mist
                drawPath(ridge(listOf(40f to 112f, 150f to 96f, 290f to 116f, 400f to 104f), STAGE_DP, -6f * u * shift), Tokens.SkyMid.copy(alpha = 0.35f))
                drawMist(u, ::y, 116f, 140f, 0.09f, 10f * sin(2 * PI * t / 14f).toFloat())
                // 7 middle mountains
                drawPath(ridge(listOf(0f to 136f, 110f to 118f, 240f to 130f, 411f to 112f), STAGE_DP, -12f * u * shift), midFill)
                translate(left = -20f * u * shift) {
                    drawPagoda(u, ::y) // 8
                    scope.lanterns?.let { (total, lit) -> drawLanterns(u, ::y, total, lit) } // 10: between the middle and near mountains
                }
                // 9 second mist
                drawMist(u, ::y, 140f, 158f, 0.06f, 10f * sin(2 * PI * t / 19f).toFloat())
                // 11 near ridge with a moonlit edge, 12 grass
                val near = Path().apply {
                    moveTo(-20f * u + (-20f * u * shift), y(160f))
                    cubicTo(30f * u, y(160f), 50f * u, y(166f), 70f * u, y(166f))
                    lineTo(250f * u, y(166f))
                    cubicTo(330f * u, y(166f), 360f * u, y(150f), (W + 20f) * u, y(150f))
                    lineTo((W + 20f) * u, y(STAGE_DP * 4)); lineTo(-20f * u, y(STAGE_DP * 4)); close()
                }
                drawPath(near, Tokens.NightDeep)
                drawLine(Tokens.NightShade, Offset(70f * u, y(166f)), Offset(250f * u, y(166f)), 1f * u)
                for ((gx, gh, ph) in GRASS) {
                    val lean = 6f * sin(2 * PI * (t / 3.2f + ph)).toFloat()
                    rotate(lean, Offset(gx * u, y(166f))) { drawLine(Tokens.NightShade, Offset(gx * u, y(166f)), Offset(gx * u, y(166f - gh)), 1.5f * u, StrokeCap.Round) }
                }
                // 13 Beom, 14 Deung
                val nowMs = t * 1000f
                val pose = if (still) when (scope.mood) { // one frame, no movement
                    StageMood.Idle -> BeomPose.Rest
                    StageMood.Won -> BeomPose.Happy
                    StageMood.Lost -> BeomPose.Sad
                } else (scope.director.pose(nowMs) ?: beomIdle(t)).let { p -> if (scope.director.playing == "A1") p.copy(lookX = scope.director.lookX) else p }
                scope.director.dust(nowMs)?.let { x -> // A3's landing: two clouds of dust, 10 → 18 dp, fading
                    for (side in listOf(-20f, 20f)) drawOval(Tokens.SkyLight.copy(alpha = 0.4f * (1f - x)), Offset((150f + side - (10f + 8f * x)) * u, y(166f - 3f)), Size(2 * (10f + 8f * x) * u, 2 * 4f * u))
                }
                if (!scope.entering) {
                    drawBeom(u, 150f * u, y(166f), pose)
                    val float = if (still) 0f else 3f * sin(2 * PI * t / 2.4f).toFloat()
                    val lit = scope.streak >= 3
                    val glow = if (still) when (scope.mood) { StageMood.Won -> 1.3f; StageMood.Lost -> 0.7f; else -> if (lit) 1.3f else 1f }
                        else scope.director.deungGlow(nowMs) ?: ((if (lit) 1.3f else 1f) + 0.08f * sin(2 * PI * t / 2.4f).toFloat())
                    val ignite = if (still) null else scope.director.flameScale(nowMs)
                    val flicker = if (still) 1f else 1f + 0.1f * sin(2 * PI * t / 0.3f).toFloat() // 0.9–1.1, 300 ms
                    drawDeung(u, 206f * u, y(112f + float), DeungPose(glow = glow, eyes = if (scope.mood == StageMood.Won) DeungEyes.Happy else DeungEyes.Open, flame = lit || ignite != null, flameScale = ignite ?: 1f, flameHeight = flicker))
                    scope.director.smoke(nowMs)?.let { x -> // the flame went out: a ring of smoke, 6 → 16 dp
                        drawCircle(Tokens.SkyLight.copy(alpha = 0.3f * (1f - x)), (6f + 10f * x) * u, Offset(206f * u, y(112f + float - 17f)), style = Stroke(1.5f * u))
                    }
                }
                // 15 particles
                if (petals.value < 1f) drawPetals(u, petals.value)
            }
        },
    )
}

private fun DrawScope.drawSparkle(c: Offset, r: Float) {
    val p = Path().apply {
        moveTo(c.x, c.y - r); quadraticTo(c.x, c.y, c.x + r, c.y); quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y); quadraticTo(c.x, c.y, c.x, c.y - r); close()
    }
    drawPath(p, Color.White.copy(alpha = 0.8f))
}

/** A band of mist between two ridges: white 0 → [peak] → 0, drifting [dx] dp. */
private fun DrawScope.drawMist(u: Float, y: (Float) -> Float, top: Float, bottom: Float, peak: Float, dx: Float) {
    translate(left = dx * u) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = peak), Color.Transparent), startY = y(top), endY = y(bottom)), Offset(-20f * u, y(top)), Size(size.width + 40f * u, y(bottom) - y(top)))
    }
}

private fun DrawScope.drawMoon(u: Float, y: (Float) -> Float, timer: (() -> Float)?, pulse: Float, face: DalFace?) {
    val center = Offset(262f * u, y(74f))
    val left = timer?.invoke()
    val beat = if (left != null && left < 0.25f && left > 0f) 1f + 0.06f * sin(PI * pulse).toFloat() else 1f
    drawCircle(Brush.radialGradient(listOf(Tokens.SkyLight.copy(alpha = 0.30f), Color.Transparent), center, 46f * u), 46f * u, center)
    drawCircle(Brush.radialGradient(listOf(Color.White, Tokens.SkyLight), Offset(center.x - 6f * u, center.y - 6f * u), 30f * u), 22f * u * beat, center)
    for ((cx, cy, w, h) in listOf(listOf(-7f, -6f, 9f, 7f), listOf(6f, 4f, 7f, 6f), listOf(-3f, 9f, 6f, 5f))) { // craters
        drawOval(Tokens.SkyMid.copy(alpha = 0.18f), Offset(center.x + (cx - w / 2) * u, center.y + (cy - h / 2) * u), Size(w * u, h * u))
    }
    if (left != null && left > 0f) { // 4.1: a ring of 3 dp, radius 29 dp, consumed clockwise from 12
        val ring = 29f * u
        val topLeft = Offset(center.x - ring, center.y - ring)
        val stroke = Stroke(3f * u, cap = StrokeCap.Round)
        drawArc(Tokens.SkyMid.copy(alpha = 0.4f), -90f, 360f, false, topLeft, Size(ring * 2, ring * 2), style = stroke)
        drawArc(if (left < 0.25f) Tokens.Lantern else Color.White, -90f + 360f * (1 - left), 360f * left, false, topLeft, Size(ring * 2, ring * 2), style = stroke)
    }
    drawDalFace(u, center.x, center.y, face ?: if (left != null && left in 0.0001f..0.25f) DalFace.Nervous else DalFace.Calm)
}

private fun DrawScope.drawPagoda(u: Float, y: (Float) -> Float) {
    val x = 332f * u
    val base = y(140f)
    val w = 40f * u
    val h = 34f * u
    val roof = Path().apply { // two curved roofs and a finial
        moveTo(x - 4f * u, base - h * 0.42f); quadraticTo(x + w / 2, base - h * 0.62f, x + w + 4f * u, base - h * 0.42f)
        lineTo(x + w * 0.8f, base - h * 0.58f); lineTo(x + w * 0.2f, base - h * 0.58f); close()
        moveTo(x - 1f * u, base - h * 0.86f); quadraticTo(x + w / 2, base - h * 1.04f, x + w + 1f * u, base - h * 0.86f)
        lineTo(x + w * 0.72f, base - h * 1.0f); lineTo(x + w * 0.28f, base - h * 1.0f); close()
    }
    drawRect(Tokens.NightDeep, Offset(x + w * 0.18f, base - h * 0.42f), Size(w * 0.64f, h * 0.42f))
    drawRect(Tokens.NightDeep, Offset(x + w * 0.26f, base - h * 0.86f), Size(w * 0.48f, h * 0.30f))
    drawPath(roof, Tokens.NightDeep)
    drawLine(Tokens.NightDeep, Offset(x + w / 2, base - h * 1.0f), Offset(x + w / 2, base - h * 1.25f), 1.5f * u)
    for (wx in listOf(0.34f, 0.58f)) drawRect(Tokens.Lantern.copy(alpha = 0.8f), Offset(x + w * wx, base - h * 0.3f), Size(3f * u, 4f * u))
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
