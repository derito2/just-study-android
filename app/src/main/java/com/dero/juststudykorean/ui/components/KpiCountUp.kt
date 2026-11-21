package com.dero.juststudykorean.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.reduceMotion

/** M-5 (design/polish.md P-06): the KPI figures count up only the first time the Dashboard shows its data after the app starts. */
object KpiCountUp {
    @Volatile private var consumed = false

    /** The Dashboard has shown its data: from now on the figures are simply there. */
    fun consume() { consumed = true }

    internal fun shouldAnimate() = !consumed

    internal fun reset() { consumed = false }
}

/** [target] counted from 0 in 400 ms (ease-out) if it is the first time; null while loading. With "Remove animations", at once. */
@Composable
fun rememberKpiCount(target: Long?): Long? {
    val still = reduceMotion() || LocalFrozenMotion.current
    val animate = remember { KpiCountUp.shouldAnimate() && !still }
    val t = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(target) {
        if (animate && target != null) { t.snapTo(0f); t.animateTo(1f, tween(Motion.Slow, easing = Motion.EaseOut)) }
    }
    return target?.let { Math.round(it * t.value.toDouble()) }
}
