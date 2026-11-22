package com.dero.juststudykorean.ui.games.stage

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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.dero.juststudykorean.ui.theme.Tokens

// design/games-characters.md 4: Deung, the floating red lantern (the streak, the lives), and Dal's face.

enum class DeungEyes { Open, Happy, Shut, Wide }

/** [glow] 0…1.6; [flame]: the streak flame over the lid (streak ≥ 3). */
data class DeungPose(val tilt: Float = 0f, val glow: Float = 1f, val sx: Float = 1f, val sy: Float = 1f, val eyes: DeungEyes = DeungEyes.Open, val flame: Boolean = false)

/** Deung centred on ([cx], [cy]) px; [u] is px per dp. At most ~14 draw operations. */
fun DrawScope.drawDeung(u: Float, cx: Float, cy: Float, pose: DeungPose) {
    val g = pose.glow.coerceIn(0f, 1.6f)
    withTransform({ translate(cx, cy); rotate(pose.tilt, Offset.Zero); scale(u * pose.sx, u * pose.sy, Offset.Zero) }) {
        drawCircle(Brush.radialGradient(listOf(Tokens.Lantern.copy(alpha = 0.30f * g), Color.Transparent), Offset.Zero, 22f), 22f, Offset.Zero)
        drawLine(Tokens.SkyLight.copy(alpha = 0.6f), Offset(0f, -12f), Offset(0f, -22f), 1.5f)
        if (pose.flame) drawPath(Path().apply { moveTo(0f, -26f); quadraticTo(5f, -20f, 0f -0f, -13.5f); quadraticTo(-5f, -20f, 0f, -26f); close() }.let { it }, Tokens.Lantern)
        if (pose.flame) drawPath(Path().apply { moveTo(0f, -22f); quadraticTo(2f, -19f, 0f, -16f); quadraticTo(-2f, -19f, 0f, -22f); close() }, Color.White)
        drawRoundRect(Tokens.NightDeep, Offset(-6f, -14f), Size(12f, 4f), CornerRadius(2f)) // lid
        drawRoundRect(Tokens.LanternRed, Offset(-11f, -12f), Size(22f, 24f), CornerRadius(9f)) // body
        for (x in listOf(-4f, 0f, 4f)) drawLine(Tokens.NightDeep.copy(alpha = 0.25f), Offset(x, -10f), Offset(x, 10f), 1f)
        drawRoundRect(Tokens.NightDeep, Offset(-5f, 11f), Size(10f, 3f), CornerRadius(1.5f)) // base
        drawCircle(Brush.radialGradient(listOf(Tokens.Lantern.copy(alpha = 0.9f * (g / 1.6f).coerceAtMost(1f)), Color.Transparent), Offset.Zero, 9f), 9f, Offset.Zero)
        for (side in listOf(-1f, 1f)) { // face
            val x = side * 4f
            when (pose.eyes) {
                DeungEyes.Open -> drawCircle(Tokens.NightDeep, 1.5f, Offset(x, -1f))
                DeungEyes.Wide -> drawCircle(Tokens.NightDeep, 2.2f, Offset(x, -1f))
                DeungEyes.Happy -> drawPath(Path().apply { moveTo(x - 2f, 0f); quadraticTo(x, -3f, x + 2f, 0f) }, Tokens.NightDeep, style = Stroke(1f, cap = StrokeCap.Round))
                DeungEyes.Shut -> drawLine(Tokens.NightDeep, Offset(x - 2f, -1f), Offset(x + 2f, -1f), 1f, StrokeCap.Round)
            }
        }
        drawPath(Path().apply { moveTo(-2f, 3f); quadraticTo(0f, 5f, 2f, 3f) }, Tokens.NightDeep, style = Stroke(1f, cap = StrokeCap.Round))
        drawLine(Tokens.Lantern, Offset(-2f, 14f), Offset(-3f, 20f), 1f, StrokeCap.Round) // tassel
        drawLine(Tokens.Lantern, Offset(2f, 14f), Offset(3f, 20f), 1f, StrokeCap.Round)
    }
}

/** Dal's states: Calm has no face (the default); Nervous is the last quarter of the clock; Sleepy is the pause; Proud is the summary. */
enum class DalFace { Calm, Nervous, Sleepy, Proud }

/** The face of the moon disc, centred on ([cx], [cy]) px with a radius of 22 dp. */
fun DrawScope.drawDalFace(u: Float, cx: Float, cy: Float, face: DalFace) {
    if (face == DalFace.Calm) return
    val ink = Tokens.NightShade.copy(alpha = 0.5f)
    withTransform({ translate(cx, cy); scale(u, u, Offset.Zero) }) {
        when (face) {
            DalFace.Nervous -> {
                for (side in listOf(-1f, 1f)) drawCircle(ink, 2f, Offset(side * 7f, -3f))
                drawPath(Path().apply { moveTo(-6f, 8f); cubicTo(-3f, 5f, -1f, 11f, 2f, 8f); cubicTo(3.5f, 6f, 5f, 9f, 6f, 8f) }, ink, style = Stroke(1.6f, cap = StrokeCap.Round))
                drawOval(Tokens.SkyMid, Offset(17f, -8f), Size(3f, 5f)) // a drop
            }
            DalFace.Sleepy -> {
                for (side in listOf(-1f, 1f)) drawPath(Path().apply { moveTo(side * 7f - 3.5f, -2f); quadraticTo(side * 7f, 2f, side * 7f + 3.5f, -2f) }, ink, style = Stroke(1.8f, cap = StrokeCap.Round))
                drawLine(ink, Offset(-3f, 9f), Offset(3f, 9f), 1.6f, StrokeCap.Round)
            }
            DalFace.Proud -> {
                for (side in listOf(-1f, 1f)) drawPath(Path().apply { moveTo(side * 7f - 3.5f, 0f); quadraticTo(side * 7f, -5f, side * 7f + 3.5f, 0f) }, ink, style = Stroke(1.8f, cap = StrokeCap.Round))
                drawPath(Path().apply { moveTo(-6f, 6f); quadraticTo(0f, 13f, 6f, 6f) }, ink, style = Stroke(1.8f, cap = StrokeCap.Round))
            }
            DalFace.Calm -> Unit
        }
    }
}
