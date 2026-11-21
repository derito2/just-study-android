package com.dero.juststudykorean.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Motion

/** M-1 (design/polish.md P-06): a progress bar whose fill grows to its new width in 240 ms (ease-out): you moved one on. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val c = JustStudyTheme.colors
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(Motion.Base, easing = Motion.EaseOut), label = "progress")
    LinearProgressIndicator(
        { shown }, modifier, color = c.brand, trackColor = c.canvas, gapSize = 0.dp, drawStopIndicator = {}, strokeCap = StrokeCap.Round,
    )
}
