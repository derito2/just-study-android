package com.dero.juststudykorean.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.sin

/** Lucide Animated icons used by the shell (design/shell.md and the design system README, section Iconos). */
enum class Lucide(internal val durationMs: Int) {
    LayoutGrid(800), BookText(600), Layers(600), CircleHelp(500), GraduationCap(700), Menu(300),
    Languages(1000), TrendingUp(400), CalendarDays(400), Moon(1200), Settings(600), ChevronLeft(500), BadgeAlert(500), Download(500), TrendingDown(400), Minus(300),
    Check(300), X(300), SquarePen(500), Trash2(500), Search(400), Plus(300), RefreshCw(600), FileText(0),
}

/** How one part of an icon moves at progress t (0..1). Pivot in icon units (24 × 24). */
private data class PartPose(
    val dx: Float = 0f, val dy: Float = 0f, val rotation: Float = 0f, val pivot: Offset = Offset(12f, 12f),
    val scale: Float = 1f, val trimEnd: Float = 1f, val alpha: Float = 1f,
)

private fun wave(t: Float) = sin(PI * t).toFloat() // 0 → 1 → 0
private fun swing(t: Float) = sin(2 * PI * t).toFloat() // 0 → 1 → -1 → 0

// Approximations of each icon's .tsx animation (README table): same idea, duration and one-shot.
private fun Lucide.pose(part: Int, t: Float): PartPose = when (this) {
    Lucide.LayoutGrid -> {
        val toCenter = listOf(Offset(1f, 1f), Offset(-1f, 1f), Offset(-1f, -1f), Offset(1f, -1f))[part] * (1.5f * wave(t))
        PartPose(dx = toCenter.x, dy = toCenter.y)
    }
    Lucide.BookText -> PartPose(rotation = 8f * swing(t), dy = -2f * wave(t))
    Lucide.Layers -> PartPose(dy = -listOf(0f, 9f, 5f)[part] * wave(t))
    Lucide.CircleHelp -> if (part == 0) PartPose() else PartPose(rotation = 10f * swing(t))
    Lucide.GraduationCap -> PartPose(dy = -2f * wave(t), rotation = if (part == 2) 10f * swing(t) else 0f, pivot = Offset(22f, 10f))
    Lucide.Menu -> when (part) { // the three lines turn into an X and back
        0 -> PartPose(dy = 6f * wave(t), rotation = 45f * wave(t))
        1 -> PartPose(alpha = 1f - wave(t))
        else -> PartPose(dy = -6f * wave(t), rotation = -45f * wave(t))
    }
    Lucide.Languages -> PartPose(trimEnd = ((t * 1000f - part * 100f) / 500f).coerceIn(0f, 1f)) // drawn in a chain
    Lucide.TrendingUp, Lucide.TrendingDown -> PartPose(trimEnd = t)
    Lucide.Minus, Lucide.Check, Lucide.X, Lucide.Plus -> PartPose(trimEnd = t)
    Lucide.SquarePen -> PartPose(rotation = 10f * swing(t), pivot = Offset(4f, 20f))
    Lucide.Trash2 -> PartPose(rotation = 8f * swing(t) * (1f - t))
    Lucide.Search -> PartPose(scale = 1f + 0.1f * wave(t))
    Lucide.CalendarDays -> if (part < 4) PartPose() else PartPose(alpha = 1f - 0.8f * wave(((t * 6f) - (part - 4)).coerceIn(0f, 1f)))
    Lucide.Moon -> PartPose(rotation = 14f * swing(t) * (1f - t))
    Lucide.Settings -> PartPose(rotation = 180f * t)
    Lucide.RefreshCw -> PartPose(rotation = 180f * t)
    Lucide.ChevronLeft -> PartPose(dx = -2f * wave(t))
    Lucide.Download -> if (part == 0) PartPose() else PartPose(dy = 2f * wave(t)) // the arrow drops 2 dp
    Lucide.FileText -> PartPose() // a source line: never animated
    Lucide.BadgeAlert -> PartPose(rotation = 8f * sin(4 * PI * t).toFloat() * (1f - t), scale = 1f + 0.1f * wave(t))
}

/**
 * A Lucide icon drawn as strokes. When [animateKey] changes (not on first show), it plays its animation once;
 * never with the system animation scale at 0.
 */
@Composable
fun LucideIcon(
    icon: Lucide,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    strokeWidth: Float = 2f,
    tint: Color = LocalContentColor.current,
    animateKey: Any? = null,
) {
    val paths = remember(icon) { icon.paths.map { PathParser().parsePathString(it).toPath() } }
    val progress = remember(icon) { Animatable(1f) }
    val context = LocalContext.current
    val firstKey = remember(icon) { arrayOf(animateKey) }
    LaunchedEffect(icon, animateKey) {
        if (animateKey == firstKey[0]) return@LaunchedEffect
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (scale == 0f) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(icon.durationMs, easing = Motion.EaseOut))
    }
    val sem = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(sem).size(size)) {
        val t = progress.value
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            paths.forEachIndexed { i, path ->
                val p = if (t >= 1f) PartPose() else icon.pose(i, t)
                translate(p.dx, p.dy) {
                    rotate(p.rotation, pivot = p.pivot) {
                        scale(p.scale, pivot = Offset(12f, 12f)) {
                            drawPath(trimmed(path, p.trimEnd), tint.copy(alpha = tint.alpha * p.alpha), style = stroke)
                        }
                    }
                }
            }
        }
    }
}

private fun trimmed(path: Path, end: Float): Path {
    if (end >= 1f) return path
    val measure = PathMeasure().apply { setPath(path, false) }
    return Path().also { measure.getSegment(0f, measure.length * end, it, true) }
}
