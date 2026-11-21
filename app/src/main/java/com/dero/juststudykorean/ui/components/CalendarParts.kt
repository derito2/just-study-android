package com.dero.juststudykorean.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.Tokens
import com.dero.juststudykorean.ui.theme.reduceMotion

/**
 * P-13: the ring of today, 2 dp, drawn OUTSIDE the cell (2 dp apart from it), so "today" and "studied" are not mixed up.
 * [progress] 0 → 1 draws it all the way round (M-6); the cell is at (0, 0) with [size].
 */
internal fun DrawScope.drawTodayRing(color: Color, progress: Float, size: Size, cornerRadiusPx: Float, gapPx: Float, strokePx: Float) {
    if (progress <= 0f) return
    val out = gapPx + strokePx / 2f
    val path = Path().apply { addRoundRect(RoundRect(-out, -out, size.width + out, size.height + out, CornerRadius(cornerRadiusPx + out))) }
    val measure = PathMeasure().apply { setPath(path, false) }
    val part = Path()
    measure.getSegment(0f, measure.length * progress.coerceAtMost(1f), part, true)
    drawPath(part, color, style = Stroke(strokePx, cap = StrokeCap.Round))
}

/** The ring of today around a cell; it draws itself in 400 ms (ease-in-out) when the calendar opens, once. Still with "Remove animations". */
@Composable
internal fun Modifier.todayRing(color: Color): Modifier {
    val still = reduceMotion() || LocalFrozenMotion.current
    val sweep = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { if (!still) sweep.animateTo(1f, tween(Motion.Slow, easing = Motion.EaseInOut)) }
    return drawBehind { drawTodayRing(color, sweep.value, size, Tokens.RadiusSm.toPx(), 2.dp.toPx(), 2.dp.toPx()) }
}

/** P-13: the legend in a 2 × 2 grid, 12 sp `muted`, each swatch 12 dp with its symbol inside. [long]: the wording of the Calendar screen. */
@Composable
internal fun CalendarLegend(long: Boolean, modifier: Modifier = Modifier) {
    val c = JustStudyTheme.colors
    val items = listOf(
        "green" to if (long) "accuracy 80 % or more" else "80 %+", "yellow" to if (long) "accuracy 50–79 %" else "50–79 %",
        "red" to if (long) "accuracy under 50 %" else "under 50 %", "none" to "not studied",
    )
    Column(modifier.fillMaxWidth().semantics { contentDescription = "Legend" }, verticalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (status, label) ->
                    val (fill, ink) = c.cell(status)
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        // "not studied" is the colour of the card in dark: a thin border keeps its swatch visible.
                        Box(
                            Modifier.size(12.dp).background(fill, RoundedCornerShape(3.dp)).then(if (status == "none") Modifier.border(1.dp, c.line, RoundedCornerShape(3.dp)) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (symbol(status).isNotEmpty()) Text(symbol(status), style = TextStyle(fontSize = 9.sp), color = ink)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = TextStyle(fontSize = 12.sp), color = c.muted)
                    }
                }
            }
        }
    }
}

/** P-47: the zone note, inside the card, with a 14 dp clock. */
@Composable
internal fun ZoneNote(text: String, modifier: Modifier = Modifier) {
    val c = JustStudyTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LucideIcon(Lucide.Clock, contentDescription = null, size = 14.dp, tint = c.muted)
        Spacer(Modifier.width(6.dp))
        Text(text, style = TextStyle(fontSize = 12.sp), color = c.muted, modifier = Modifier.alpha(1f))
    }
}
