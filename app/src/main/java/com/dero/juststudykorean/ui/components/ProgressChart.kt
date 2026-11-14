package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.data.progress.Timeline
import com.dero.juststudykorean.data.progress.TimelinePoint
import com.dero.juststudykorean.data.progress.TimelineUnit
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The web's niceMax: the left scale rounded up (5, then 1, 2… × a power of 10). */
internal fun niceMax(value: Int): Int {
    if (value <= 5) return 5
    val step = 10.0.pow(floor(log10(value.toDouble())))
    return (ceil(value / step) * step).toInt()
}

/** R1-27: what TalkBack reads: the period's totals, so the chart is not just a picture. */
internal fun chartDescription(timeline: Timeline): String {
    val p = timeline.points
    val minutes = p.sumOf { it.minutes }
    val answers = p.sumOf { it.answers }
    val right = p.sumOf { pt -> pt.accuracy?.let { Math.round(it * pt.answers).toInt() } ?: 0 }
    val unit = timeline.unit.name.lowercase()
    val accuracy = if (answers > 0) ", ${Math.round(100.0 * right / answers)}% right" else ""
    return "Progress over time, by $unit, last ${p.size} ${unit}s: $minutes min studied, $answers quiz answers$accuracy."
}

/** Every how many periods a label is written, so they don't overlap (the web's `every`, 56 dp per label). */
internal fun labelEvery(points: Int, plotWidthDp: Float): Int = max(1, ceil(points / max(1f, floor(plotWidthDp / 56f))).toInt())

/** The web's `<title>` of a period. */
internal fun periodText(p: TimelinePoint, unit: TimelineUnit): String =
    "${periodLabel(p.start, unit.name.lowercase())}: ${p.minutes} min, quiz ${p.accuracy?.let { "${Math.round(it * 100)} %" } ?: "—"} " +
        "(${plural(p.answers, "answer")}), flashcards ${p.cardsNew} new, ${p.cardsLearning} learning, ${p.cardsDue} due"

private val padLeft = 36.dp
private val padRight = 40.dp
private val padTop = 8.dp
private val padBottom = 22.dp
private val chartHeight = 170.dp

/** Progress (design/dashboard.md §4): Day/Week/Month/Year, bars of cards, study time and quiz accuracy lines. */
@Composable
fun ProgressChart(timeline: Timeline?, unit: TimelineUnit, onUnit: (TimelineUnit) -> Unit, modifier: Modifier = Modifier) {
    val colors = JustStudyTheme.colors
    Column(
        modifier.fillMaxWidth()
            .border(Tokens.Border, colors.line, MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .padding(Tokens.Space4),
    ) {
        Row(Modifier.semantics { heading() }, verticalAlignment = Alignment.Bottom) {
            Text("Progress", Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            KoSide("진도", MaterialTheme.typography.titleMedium.fontSize, Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(Tokens.Space3))
        SelectedGroup(
            options = TimelineUnit.entries.map { it.name },
            ko = listOf("일", "주", "월", "년"),
            selected = unit.ordinal,
            onSelect = { onUnit(TimelineUnit.entries[it]) },
            modifier = Modifier.semantics { contentDescription = "Show by" },
        )
        Spacer(Modifier.height(Tokens.Space3))
        if (timeline == null || timeline.unit != unit) {
            Text("Loading…", Modifier.height(chartHeight), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        } else {
            Chart(timeline)
        }
        Spacer(Modifier.height(Tokens.Space3))
        Legend()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val c = JustStudyTheme.colors
    FlowRow(Modifier.semantics { contentDescription = "Chart legend" }, horizontalArrangement = Arrangement.spacedBy(Tokens.Space4), verticalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
        listOf(
            Triple("Study time (min)", c.action, true), Triple("Quiz accuracy (%)", c.orange, true),
            Triple("New flashcards", c.countNew, false), Triple("Learning", c.fix, false), Triple("Due", c.wrong, false),
        ).forEach { (label, color, line) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(if (line) Modifier.size(16.dp, 3.dp).background(color) else Modifier.size(12.dp).background(color, RoundedCornerShape(2.dp)))
                Text(label, style = TextStyle(fontSize = 12.sp), color = c.muted)
            }
        }
    }
}

@Composable
private fun Chart(timeline: Timeline) {
    val c = JustStudyTheme.colors
    val points = timeline.points
    val measurer = rememberTextMeasurer()
    var tapped by remember(timeline) { mutableStateOf<Int?>(null) }
    val empty = points.all { it.minutes == 0L && it.answers == 0 && it.cardsNew + it.cardsLearning + it.cardsDue == 0 }
    BoxWithConstraints(Modifier.fillMaxWidth().height(chartHeight)) {
        val plotW = max(maxWidth.value - padLeft.value - padRight.value, 50f)
        val every = labelEvery(points.size, plotW)
        val axis = TextStyle(fontSize = 10.sp, color = c.muted)
        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = chartDescription(timeline) }
                .pointerInput(points) {
                    detectTapGestures { pos ->
                        val slot = plotW.dp.toPx() / points.size
                        val i = ((pos.x - padLeft.toPx()) / slot).toInt()
                        tapped = if (i in points.indices && tapped != i) i else null
                    }
                },
        ) {
            val w = plotW.dp.toPx()
            val top = padTop.toPx()
            val h = max(size.height - top - padBottom.toPx(), 40.dp.toPx())
            val left = padLeft.toPx()
            val slot = w / points.size
            val barW = max(2.dp.toPx(), min(14.dp.toPx(), slot * 0.7f / 3))
            val leftMax = niceMax(max(1, points.maxOf { maxOf(it.cardsNew, it.cardsLearning, it.cardsDue) }))
            val minutesMax = max(1L, points.maxOf { it.minutes }) * 1.15f
            fun x(i: Int) = left + slot * i + slot / 2
            fun yLeft(v: Float) = top + h - v / leftMax * h
            fun yMinutes(v: Float) = top + h - v / minutesMax * h
            fun yRight(v: Float) = top + h - v * h
            for (t in listOf(0f, 0.5f, 1f)) {
                val y = top + h - t * h
                drawLine(c.line, Offset(left, y), Offset(left + w, y), 1.dp.toPx(), pathEffect = if (t == 0f) null else PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                label(measurer, "${Math.round(leftMax * t)}", axis, Offset(left - 6.dp.toPx(), y), alignEnd = true)
                label(measurer, "${Math.round(t * 100)}%", axis, Offset(left + w + 6.dp.toPx(), y), alignEnd = false)
            }
            points.forEachIndexed { i, p ->
                listOf(p.cardsNew to c.countNew, p.cardsLearning to c.fix, p.cardsDue to c.wrong).forEachIndexed { j, (v, color) ->
                    if (v > 0) {
                        val y = yLeft(v.toFloat())
                        drawRoundRect(color, Offset(x(i) + (j - 1.5f) * barW, y), Size(barW - 1.dp.toPx(), top + h - y), CornerRadius(2.dp.toPx()))
                    }
                }
                if (i % every == 0) {
                    val text = measurer.measure(periodLabel(p.start, timeline.unit.name.lowercase()), axis)
                    drawText(text, topLeft = Offset(x(i) - text.size.width / 2f, size.height - 6.dp.toPx() - text.size.height * 0.8f))
                }
            }
            polyline(points.map { it.minutes.toFloat() }, ::x, ::yMinutes, c.action)
            polyline(points.map { it.accuracy?.toFloat() }, ::x, ::yRight, c.orange)
            points.forEachIndexed { i, p ->
                if (p.minutes > 0) drawCircle(c.action, 2.5.dp.toPx(), Offset(x(i), yMinutes(p.minutes.toFloat())))
                p.accuracy?.let { drawCircle(c.orange, 3.dp.toPx(), Offset(x(i), yRight(it.toFloat()))) }
            }
            if (empty) {
                val text = measurer.measure("No study yet in this period.", TextStyle(fontSize = 13.sp, color = c.muted))
                drawText(text, topLeft = Offset(left + w / 2 - text.size.width / 2f, top + h / 2 - text.size.height / 2f))
            }
        }
        tapped?.let { i -> Tooltip(periodText(points[i], timeline.unit), centerX = padLeft + (plotW / points.size * (i + 0.5f)).dp) }
    }
}

/** The tapped period's text over the chart, centered on it and kept inside the chart. */
@Composable
private fun Tooltip(text: String, centerX: Dp) {
    val density = LocalDensity.current
    Text(
        text,
        Modifier
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = min(constraints.maxWidth, with(density) { 260.dp.roundToPx() })))
                val x = (with(density) { centerX.roundToPx() } - p.width / 2).coerceIn(0, constraints.maxWidth - p.width)
                layout(constraints.maxWidth, p.height) { p.place(x, 0) }
            }
            .background(JustStudyTheme.colors.tooltip, RoundedCornerShape(Tokens.RadiusSm))
            .padding(horizontal = Tokens.Space2, vertical = Tokens.Space1),
        style = TextStyle(fontSize = 12.sp),
        color = Color.White,
    )
}

private fun DrawScope.label(measurer: TextMeasurer, text: String, style: TextStyle, at: Offset, alignEnd: Boolean) {
    val m = measurer.measure(text, style)
    drawText(m, topLeft = Offset(if (alignEnd) at.x - m.size.width else at.x, at.y - m.size.height / 2f))
}

/** A line through the values; a null cuts it (a period with no answers). */
private fun DrawScope.polyline(values: List<Float?>, x: (Int) -> Float, y: (Float) -> Float, color: Color) {
    var path: Path? = null
    values.forEachIndexed { i, v ->
        if (v == null) { path?.let { drawPath(it, color, style = Stroke(2.5.dp.toPx())) }; path = null; return@forEachIndexed }
        val p = path ?: Path().also { it.moveTo(x(i), y(v)); path = it }
        p.lineTo(x(i), y(v))
    }
    path?.let { drawPath(it, color, style = Stroke(2.5.dp.toPx())) }
}
