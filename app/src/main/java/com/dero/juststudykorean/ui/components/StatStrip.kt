package com.dero.juststudykorean.ui.components

import com.dero.juststudykorean.ui.theme.reduceMotion
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/** One figure of a [StatStrip]. [color] only when the figure means something (`correctInk`, `wrongInk`); [ko] is its Korean label. */
class Stat(val label: String, val value: String, val ko: String? = null, val color: Color? = null, val pulse: Boolean = false)

/**
 * design/polish.md P-02: a row of figures of equal width. Plain (no frame): the caps label in `muted` with its Korean beside it,
 * and the value at 20 sp, 800. Tiled: each figure in its own card, the value at 22 sp.
 */
@Composable
fun StatStrip(stats: List<Stat>, modifier: Modifier = Modifier, tiled: Boolean = false) {
    val muted = JustStudyTheme.colors.muted
    val stacked = !tiled && stats.size > 3
    Row(
        modifier.fillMaxWidth().then(if (tiled) Modifier.height(IntrinsicSize.Min) else Modifier),
        horizontalArrangement = if (tiled) Arrangement.spacedBy(Tokens.Space2) else Arrangement.Start,
    ) {
        stats.forEach { s ->
            val value = s.color ?: MaterialTheme.colorScheme.onSurface
            Column(
                Modifier.weight(1f).then(
                    if (tiled) Modifier.fillMaxHeight()
                        .border(Tokens.Border, JustStudyTheme.colors.line, MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                        .padding(horizontal = 4.dp, vertical = 10.dp)
                    else Modifier,
                ).clearAndSetSemantics { contentDescription = "${s.label}: ${s.value}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (tiled) Arrangement.Center else Arrangement.Top,
            ) {
                // The Korean goes beside the label; with four figures there is no room (95 dp each) and it goes under it, in all of them.
                if (stacked) {
                    Text(s.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1, textAlign = TextAlign.Center)
                    if (s.ko != null) Text(s.ko, style = JustStudyTheme.korean.koBelow, color = muted, maxLines = 1)
                } else Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        s.label.uppercase(), Modifier.alignByBaseline(), style = MaterialTheme.typography.labelMedium.let { if (tiled) it.copy(fontSize = 11.sp) else it },
                        color = muted, maxLines = 1, textAlign = TextAlign.Center,
                    )
                    if (s.ko != null && !tiled) KoSide(s.ko, MaterialTheme.typography.labelMedium.fontSize, Modifier.alignByBaseline().padding(start = 2.dp), color = muted)
                }
                StatValue(
                    s.value, s.pulse, value,
                    if (tiled) MaterialTheme.typography.headlineSmall else TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
                )
            }
        }
    }
}

/** The figure. With [pulse], it goes 1 → 1.18 → 1 (M-2, 240 ms) when its value changes, not when it first appears. */
@Composable
private fun StatValue(value: String, pulse: Boolean, color: Color, style: TextStyle) {
    val still = reduceMotion() || LocalFrozenMotion.current
    val t = remember { Animatable(1f) }
    var last by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (pulse && !still && value != last) { t.snapTo(0f); t.animateTo(1f, tween(Motion.Base, easing = Motion.Spring)) }
        last = value
    }
    Text(value, Modifier.graphicsLayer { val s = Motion.pulse(t.value); scaleX = s; scaleY = s }, color = color, maxLines = 1, style = style)
}
