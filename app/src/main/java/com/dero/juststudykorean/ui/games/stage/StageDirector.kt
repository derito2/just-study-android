package com.dero.juststudykorean.ui.games.stage

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import com.dero.juststudykorean.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

// design/games-characters.md 5 and 9: the animations are data (key frames); the director turns the game's events into tracks.

/** A damped spring as a curve (Motion.Bounce is a spring spec; key frames need an easing): a few bounces that settle at 1. */
private val BounceEasing = Easing { x -> if (x >= 1f) 1f else 1f - exp(-6f * x) * cos(2f * PI.toFloat() * 2.2f * x) }

/** [ms] = when the pose is reached; [ease] shapes the segment that ends here. Beom starts each track from wherever he is. */
class Key(val ms: Int, val pose: BeomPose, val ease: Easing = Motion.EaseOut)

/** [hold]: after the last key Beom stays in its pose (until the next event); otherwise he returns to A0. */
class Track(val id: String, val keys: List<Key>, val hold: Boolean = false) {
    val total: Int = keys.last().ms

    /** The pose [ms] after the start. Numbers are interpolated; eyes, mouth, and the other discrete parts take the segment's target. */
    fun sample(ms: Float, from: BeomPose): BeomPose {
        if (ms >= total) return keys.last().pose
        var prevMs = 0f
        var prev = from
        for (k in keys) {
            if (ms < k.ms) {
                val x = if (k.ms == prevMs.toInt()) 1f else k.ease.transform(((ms - prevMs) / (k.ms - prevMs)).coerceIn(0f, 1f))
                return lerp(prev, k.pose, x)
            }
            prevMs = k.ms.toFloat(); prev = k.pose
        }
        return keys.last().pose
    }
}

private fun l(a: Float, b: Float, x: Float) = a + (b - a) * x

internal fun lerp(a: BeomPose, b: BeomPose, x: Float) = BeomPose(
    sx = l(a.sx, b.sx, x), sy = l(a.sy, b.sy, x), y = l(a.y, b.y, x), rot = l(a.rot, b.rot, x), tilt = l(a.tilt, b.tilt, x),
    earL = l(a.earL, b.earL, x), earR = l(a.earR, b.earR, x), armL = l(a.armL, b.armL, x), armR = l(a.armR, b.armR, x),
    tail = l(a.tail, b.tail, x), tailPuff = l(a.tailPuff, b.tailPuff, x), lookX = l(a.lookX, b.lookX, x), lookY = l(a.lookY, b.lookY, x),
    blush = l(a.blush, b.blush, x), blink = l(a.blink, b.blink, x), eyes = if (x > 0f) b.eyes else a.eyes, mouth = if (x > 0f) b.mouth else a.mouth,
)

/** The tracks of the sheet. Durations are the sheet's; the last pose of each is Beom's rest, except A4 (holds). */
object Tracks {
    private val rest = BeomPose.Rest

    /** A2, 400 ms: a correct piece. */
    val A2 = Track("A2", listOf(
        Key(70, BeomPose(sx = 1.08f, sy = 0.92f), Motion.EaseIn),
        Key(200, BeomPose(y = 10f, sx = 0.94f, sy = 1.08f, eyes = BeomEyes.Happy), Motion.EaseOut),
        Key(280, BeomPose(y = 0f, sx = 1.06f, sy = 0.94f, eyes = BeomEyes.Happy), Motion.EaseIn),
        Key(400, rest, BounceEasing),
    ))

    /** A3, 900 ms: round won. Dust clouds at 520 ms (see [StageDirector.dust]). */
    val A3 = Track("A3", listOf(
        Key(110, BeomPose(sx = 1.18f, sy = 0.80f, armL = 0f, armR = 0f, eyes = BeomEyes.Squeeze), Motion.EaseIn),
        Key(330, BeomPose(y = 34f, sx = 0.86f, sy = 1.20f, armL = 150f, armR = 150f, earL = -20f, earR = -20f, tail = 1f, mouth = BeomMouth.Open, eyes = BeomEyes.Happy, tilt = -8f), Motion.EaseOut),
        Key(520, BeomPose(y = 0f, sx = 0.9f, sy = 1.1f, armL = 150f, armR = 150f, tilt = 8f, mouth = BeomMouth.Open, eyes = BeomEyes.Happy), Motion.EaseIn),
        Key(620, BeomPose(sx = 1.22f, sy = 0.78f, armL = 100f, armR = 100f, eyes = BeomEyes.Happy, mouth = BeomMouth.Open), Motion.EaseIn),
        Key(900, BeomPose(blush = 1f, eyes = BeomEyes.Happy), BounceEasing),
    ))

    /** A4, 1000 ms and then it holds: round lost (also the end of A5). */
    val A4 = Track("A4", listOf(
        Key(90, BeomPose(y = 6f, sx = 0.90f, sy = 1.14f, eyes = BeomEyes.Wide, earL = 25f, earR = 25f, mouth = BeomMouth.O, tailPuff = 1f), Motion.EaseOut),
        Key(400, BeomPose(sx = 1.20f, sy = 0.82f, tilt = 14f, earL = -30f, earR = -30f, armL = 0f, armR = 0f, tail = -1f, eyes = BeomEyes.Squeeze, mouth = BeomMouth.Wobble), BounceEasing),
        Key(600, BeomPose(tilt = -8f, eyes = BeomEyes.Squeeze, mouth = BeomMouth.Wobble, tail = -1f, earL = -20f, earR = -20f), Motion.EaseInOut),
        Key(800, BeomPose(tilt = 4f, eyes = BeomEyes.Sleepy, mouth = BeomMouth.Wobble, tail = -0.6f, earL = -15f, earR = -15f), Motion.EaseInOut),
        Key(1000, BeomPose(tilt = 0f, eyes = BeomEyes.Sleepy, mouth = BeomMouth.Wobble, tail = -0.4f, earL = -10f, earR = -10f), Motion.EaseInOut),
        // Holds until the card closes: looking at the answer, pointing at it.
        Key(1250, BeomPose(eyes = BeomEyes.Open, lookY = 2f, armR = 120f, mouth = BeomMouth.Flat, earL = -10f, earR = -10f), Motion.EaseInOut),
    ), hold = true)

    /** A13 (Hangman: a lantern goes out): only A4's first 90 ms, and back. 330 ms. */
    val A13 = Track("A13", listOf(
        Key(90, BeomPose(y = 6f, sx = 0.90f, sy = 1.14f, eyes = BeomEyes.Wide, earL = 25f, earR = 25f, mouth = BeomMouth.O), Motion.EaseOut),
        Key(330, rest, Motion.EaseInOut),
    ))

    /** A9: the pause. Beom sits and dozes; holds. */
    val A9 = Track("A9", listOf(Key(300, BeomPose(sx = 1.04f, sy = 0.96f, eyes = BeomEyes.Sleepy, mouth = BeomMouth.Flat), Motion.EaseInOut)), hold = true)

    /** After the pause: a 240 ms start (0.92 × 1.10, wide eyes) and back to rest. */
    val Resume = Track("Resume", listOf(
        Key(100, BeomPose(sx = 0.92f, sy = 1.10f, eyes = BeomEyes.Wide), Motion.EaseOut),
        Key(240, rest, BounceEasing),
    ))

    /** A10, ≥ 60 % won: the victory dance (4 beats of 300 ms, twice) and a bow. 3100 ms; holds the bow's end at rest. */
    val Dance = Track("A10-won", buildList {
        var t = 0
        repeat(2) {
            for (beat in 0 until 4) {
                t += 300
                add(Key(t, BeomPose(tilt = if (beat % 2 == 0) 10f else -10f, sx = if (beat % 2 == 0) 1.08f else 0.96f, sy = if (beat % 2 == 0) 0.94f else 1.06f, armL = 120f, armR = 120f, eyes = BeomEyes.Happy, mouth = BeomMouth.Open, blush = 1f), Motion.EaseInOut))
            }
        }
        t += 300
        add(Key(t, BeomPose(tilt = 20f, eyes = BeomEyes.Happy, blush = 1f), Motion.EaseInOut))
        t += 300
        add(Key(t, BeomPose(eyes = BeomEyes.Happy, blush = 1f), Motion.EaseInOut))
    })

    /** A10, under 60 % with rounds: two nods and an arm up. */
    val Nod = Track("A10-some", listOf(
        Key(120, BeomPose(tilt = 10f), Motion.EaseInOut), Key(240, rest, Motion.EaseInOut),
        Key(360, BeomPose(tilt = 10f), Motion.EaseInOut), Key(480, BeomPose(armR = 150f, eyes = BeomEyes.Happy), Motion.EaseInOut),
    ), hold = true)

    /** A10, 0 rounds: a yawn (600 ms) stretching, then a wave. */
    val Yawn = Track("A10-none", listOf(
        Key(300, BeomPose(mouth = BeomMouth.Open, armL = 150f, armR = 150f, eyes = BeomEyes.Shut), Motion.EaseInOut),
        Key(600, BeomPose(mouth = BeomMouth.Open, armL = 150f, armR = 150f, eyes = BeomEyes.Shut), Motion.EaseInOut),
        Key(800, BeomPose(armR = 150f, eyes = BeomEyes.Happy), Motion.EaseInOut),
        Key(1000, BeomPose(armR = 120f, eyes = BeomEyes.Happy), Motion.EaseInOut),
    ), hold = true)
}

/** What the game tells the stage (games-characters.md 9). */
sealed interface StageEvent {
    data object PieceCorrect : StageEvent
    data object RoundWon : StageEvent
    data object RoundLost : StageEvent
    data object LifeLost : StageEvent
    /** The card closed: back to A0. */
    data object NextRound : StageEvent
    data object Paused : StageEvent
    data object Resumed : StageEvent
    data class SessionEnded(val rounds: Int, val won: Int) : StageEvent
}

/**
 * Turns events into tracks; a new one interrupts the one playing and starts from the pose Beom has at that moment ("una animación nueva
 * interrumpe a la anterior partiendo de la pose actual"). Time is whatever clock the caller uses, in ms.
 */
class StageDirector {
    private var track: Track? = null
    private var startMs = 0f
    private var from: BeomPose = BeomPose.Rest
    private var dustAt = -1f
    private var glowTrack: Pair<Float, Float>? = null // (startMs, target glow) for Deung

    /** Beom's pose at [nowMs] from the playing track, or null if A0 should play. */
    fun pose(nowMs: Float): BeomPose? {
        val t = track ?: return null
        val ms = nowMs - startMs
        if (ms >= t.total && !t.hold) { track = null; return null }
        return t.sample(ms, from)
    }

    private fun play(next: Track, nowMs: Float, idle: BeomPose) {
        from = pose(nowMs) ?: idle
        track = next
        startMs = nowMs
    }

    /** Deung's glow target for the current beat; null → his own idle. */
    fun deungGlow(nowMs: Float): Float? {
        val (s, g) = glowTrack ?: return null
        val x = ((nowMs - s) / 240f).coerceIn(0f, 1f)
        return l(1f, g, x)
    }

    /** The two dust clouds of A3's landing: 0..1 over 220 ms, or null. */
    fun dust(nowMs: Float): Float? {
        if (dustAt < 0f) return null
        val x = (nowMs - dustAt) / 220f
        return if (x in 0f..1f) x else null
    }

    fun handle(e: StageEvent, nowMs: Float, idle: BeomPose) {
        when (e) {
            StageEvent.PieceCorrect -> play(Tracks.A2, nowMs, idle)
            StageEvent.RoundWon -> { play(Tracks.A3, nowMs, idle); dustAt = nowMs + 520f; glowTrack = nowMs to 1.4f }
            StageEvent.RoundLost -> { play(Tracks.A4, nowMs, idle); glowTrack = nowMs to 0.5f }
            StageEvent.LifeLost -> play(Tracks.A13, nowMs, idle)
            StageEvent.NextRound -> { track = null; glowTrack = null }
            StageEvent.Paused -> { play(Tracks.A9, nowMs, idle); glowTrack = nowMs to 0.6f }
            StageEvent.Resumed -> { play(Tracks.Resume, nowMs, idle); glowTrack = null }
            is StageEvent.SessionEnded -> play(
                when { e.rounds == 0 -> Tracks.Yawn; e.won * 10 >= e.rounds * 6 -> Tracks.Dance; else -> Tracks.Nod }, nowMs, idle,
            )
        }
    }

    internal val playing: String? get() = track?.id
}
