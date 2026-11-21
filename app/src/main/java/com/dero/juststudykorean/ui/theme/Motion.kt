package com.dero.juststudykorean.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/** Durations (ms) and curves from design/shell.md. Compose already honours the system "Remove animations" scale. */
object Motion {
    const val Fast = 150
    const val Base = 240
    const val Slow = 400
    const val Press = 90

    val EaseOut = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    val Spring = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val EaseInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** M-2 (design/polish.md P-06): the figure that counts goes 1 → 1.18 → 1 as t goes 0 → 1. */
    fun pulse(t: Float): Float = 1f + 0.18f * (1f - kotlin.math.abs(2f * t.coerceIn(0f, 1f) - 1f))

    /** M-7: a banner comes in from 8 dp below, as t goes 0 → 1 (`Spring` overshoots 1, so the offset can go slightly above 0). */
    fun bannerOffsetDp(t: Float): Float = 8f * (1f - t)

    /** M-7: and fades in; the overshoot of `Spring` does not make it brighter than 1. */
    fun bannerAlpha(t: Float): Float = t.coerceIn(0f, 1f)

    // Games (design/games.md 6.2); the times above do not change.
    /** A piece that travels to a slot, a bin or back. */
    val Fly = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.72f, stiffness = 520f)
    /** A right piece: scale 1 → 1.10 → 1. */
    const val Pop = 240
    /** A wrong piece: x 0 → −6 → 6 → −4 → 4 → 0 dp. */
    const val Shake = 320
    /** Between pieces entering or leaving together (24 with more than 10). */
    const val Stagger = 40
}

/** "Remove animations" is on (animator scale 0): no wiggle, flip, pop, shake or fly. */
@androidx.compose.runtime.Composable
fun reduceMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * True in screenshot tests: endless animations stand still on their first frame. An endless animation keeps
 * Robolectric drawing frames while Roborazzi waits for the screen to settle, which made those tests take minutes
 * or never end in CI.
 */
val LocalFrozenMotion = androidx.compose.runtime.staticCompositionLocalOf { false }
