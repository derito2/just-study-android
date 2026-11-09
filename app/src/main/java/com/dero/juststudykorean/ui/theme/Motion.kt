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
}

/** "Remove animations" is on (animator scale 0): no wiggle, flip, pop, shake or fly. */
@androidx.compose.runtime.Composable
fun reduceMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
