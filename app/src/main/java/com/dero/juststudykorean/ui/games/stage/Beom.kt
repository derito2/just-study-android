package com.dero.juststudykorean.ui.games.stage

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

// design/games-characters.md 3: Beom, a white tiger cub, on his own 72 × 80 dp canvas. Origin (0, 0) = the centre of his feet, y up is negative.

enum class BeomEyes { Open, Happy, Squeeze, Wide, Sleepy, Shut }
enum class BeomMouth { Smile, Open, O, Wobble, Flat }

/** One pose; an animation is a list of these (F2). Angles in degrees, lengths in dp. */
data class BeomPose(
    val sx: Float = 1f, val sy: Float = 1f, val y: Float = 0f, val rot: Float = 0f, val tilt: Float = 0f,
    val earL: Float = 0f, val earR: Float = 0f, val armL: Float = 15f, val armR: Float = 15f,
    val tail: Float = 0f, val tailPuff: Float = 0f, val eyes: BeomEyes = BeomEyes.Open, val mouth: BeomMouth = BeomMouth.Smile,
    val lookX: Float = 0f, val lookY: Float = 0f, val blush: Float = 0f,
    /** Eye height factor for a blink (1 open → 0.1 → 1); only used with [BeomEyes.Open]. */
    val blink: Float = 1f,
) {
    companion object {
        val Rest = BeomPose()
        /** Won, with "Remove animations": eyes happy, arms up. */
        val Happy = BeomPose(eyes = BeomEyes.Happy, armL = 150f, armR = 150f, mouth = BeomMouth.Open, blush = 1f)
        /** Lost, with "Remove animations": sleepy eyes, head tilted, one arm pointing at the board. Never mocking. */
        val Sad = BeomPose(eyes = BeomEyes.Sleepy, tilt = 10f, earL = -10f, earR = -10f, armR = 95f, mouth = BeomMouth.Flat)
    }
}

/** A superellipse (n = 2.6) 52 × 44 for the head. */
private fun superellipse(cx: Float, cy: Float, w: Float, h: Float, n: Float = 2.6f): Path = Path().apply {
    val steps = 36
    for (i in 0..steps) {
        val a = 2 * PI * i / steps
        val c = cos(a).toFloat(); val s = sin(a).toFloat()
        val x = cx + w / 2 * sign(c) * abs(c).pow(2f / n)
        val y = cy + h / 2 * sign(s) * abs(s).pow(2f / n)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A rounded triangle: base at the bottom, [w] wide, [h] tall, centred on [cx] with its base at [baseY]. */
private fun roundedTriangle(cx: Float, baseY: Float, w: Float, h: Float): Path = Path().apply {
    moveTo(cx - w / 2, baseY)
    quadraticTo(cx - w / 2, baseY - h * 0.55f, cx, baseY - h)
    quadraticTo(cx + w / 2, baseY - h * 0.55f, cx + w / 2, baseY)
    close()
}

/** Draws Beom with his feet at ([footX], [footY]) in px; [u] is px per dp. At most ~40 draw operations. */
fun DrawScope.drawBeom(u: Float, footX: Float, footY: Float, pose: BeomPose) {
    val sky = Tokens.SkyLight
    val ink = Tokens.NightDeep
    val stripe = Tokens.NightShade
    withTransform({ translate(footX, footY); scale(u, u, Offset.Zero) }) {
        // Shadow: shrinks as he jumps.
        val shadowW = 44f * (1f - (pose.y / 80f).coerceIn(0f, 0.9f))
        drawOval(ink.copy(alpha = 0.45f), Offset(-shadowW / 2, -3f), Size(shadowW, 8f))
        withTransform({ translate(0f, -pose.y); rotate(pose.rot, Offset.Zero); scale(pose.sx, pose.sy, Offset.Zero) }) {
            // Tail: a 7 dp stroke from (-18, -14) to (-34, -38), turned at the root.
            rotate(18f * pose.tail, Offset(-18f, -14f)) {
                val tailPath = Path().apply { moveTo(-18f, -14f); quadraticTo(-36f, -18f, -34f, -38f) }
                drawPath(tailPath, sky, style = Stroke(7f + 3f * pose.tailPuff, cap = StrokeCap.Round))
                drawLine(stripe, Offset(-26f, -16f), Offset(-27f, -21f), 3f, StrokeCap.Round)
                drawLine(stripe, Offset(-32f, -26f), Offset(-35f, -28f), 3f, StrokeCap.Round)
                drawCircle(Color.White, 3f, Offset(-34f, -38f))
            }
            // Feet and body.
            drawRoundRect(sky, Offset(-18f, -8f), Size(14f, 8f), CornerRadius(4f))
            drawRoundRect(sky, Offset(4f, -8f), Size(14f, 8f), CornerRadius(4f))
            drawRoundRect(sky, Offset(-22f, -37f), Size(44f, 34f), CornerRadius(17f))
            drawOval(Color.White, Offset(-13f, -26f), Size(26f, 20f))
            // Arms: hang from the shoulder (±19, -30); 15° out at rest, 150° = up.
            for (side in listOf(-1f, 1f)) {
                val angle = if (side < 0) pose.armL else pose.armR
                rotate(-side * angle, Offset(side * 19f, -30f)) {
                    drawRoundRect(sky, Offset(side * 19f - 4.5f, -30f), Size(9f, 18f), CornerRadius(4.5f))
                }
            }
            // Head, tilted about (0, -32).
            rotate(pose.tilt, Offset(0f, -32f)) {
                for (side in listOf(-1f, 1f)) { // ears
                    val e = if (side < 0) pose.earL else pose.earR
                    rotate(side * e, Offset(side * 18f, -64f)) {
                        drawPath(roundedTriangle(side * 18f, -64f, 14f, 13f), sky)
                        drawPath(roundedTriangle(side * 18f, -64f, 7f, 6f), Tokens.Petal.copy(alpha = 0.6f))
                    }
                }
                drawPath(superellipse(0f, -50f, 52f, 44f), sky)
                // Stripes: forehead and cheeks.
                for ((x, y) in listOf(0f to -64f, -7f to -62f, 7f to -62f)) drawLine(stripe, Offset(x, y - 3f), Offset(x, y + 2f), 3f, StrokeCap.Round)
                for (side in listOf(-1f, 1f)) {
                    drawLine(stripe, Offset(side * 23f, -48f), Offset(side * 19f, -48f), 2.5f, StrokeCap.Round)
                    drawLine(stripe, Offset(side * 22f, -42f), Offset(side * 18f, -42f), 2.5f, StrokeCap.Round)
                }
                drawOval(Color.White, Offset(-9f, -48f), Size(18f, 12f)) // muzzle
                drawPath(roundedTriangle(0f, -43f, 6f, 4f), Tokens.Petal) // nose
                for (side in listOf(-1f, 1f)) drawCircle(Tokens.Petal.copy(alpha = 0.35f + 0.45f * pose.blush), 4f, Offset(side * 19f, -42f)) // blush
                for (side in listOf(-1f, 1f)) drawEye(side * 11f + pose.lookX, -50f + pose.lookY, pose, ink)
                drawMouth(pose.mouth, ink)
            }
        }
    }
}

private fun DrawScope.drawEye(x: Float, y: Float, pose: BeomPose, ink: Color) {
    when (pose.eyes) {
        BeomEyes.Open -> {
            val h = 9f * pose.blink
            drawOval(ink, Offset(x - 3.5f, y - h / 2), Size(7f, h))
            if (pose.blink > 0.6f) drawCircle(Color.White, 1.25f, Offset(x - 1.2f, y - h / 2 + 2f))
        }
        BeomEyes.Happy -> drawPath(Path().apply { moveTo(x - 4.5f, y + 2f); quadraticTo(x, y - 5f, x + 4.5f, y + 2f) }, ink, style = Stroke(3f, cap = StrokeCap.Round))
        BeomEyes.Squeeze -> { // "> <"
            val d = if (x < 0f) 1f else -1f
            drawLine(ink, Offset(x - d * 3.5f, y - 3.5f), Offset(x + d * 3.5f, y), 3f, StrokeCap.Round)
            drawLine(ink, Offset(x - d * 3.5f, y + 3.5f), Offset(x + d * 3.5f, y), 3f, StrokeCap.Round)
        }
        BeomEyes.Wide -> {
            drawCircle(Color.White, 5f, Offset(x, y)); drawCircle(ink, 2f, Offset(x, y)); drawCircle(Color.White, 1f, Offset(x - 0.8f, y - 0.8f))
        }
        BeomEyes.Sleepy -> drawLine(ink, Offset(x - 4f, y - 0.5f), Offset(x + 4f, y + 1f), 2.5f, StrokeCap.Round)
        BeomEyes.Shut -> drawPath(Path().apply { moveTo(x - 4.5f, y - 1f); quadraticTo(x, y + 5f, x + 4.5f, y - 1f) }, ink, style = Stroke(3f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawMouth(mouth: BeomMouth, ink: Color) {
    val y = -38f
    when (mouth) {
        BeomMouth.Smile -> drawPath(Path().apply { moveTo(-4f, y); quadraticTo(-2f, y + 3f, 0f, y); quadraticTo(2f, y + 3f, 4f, y) }, ink, style = Stroke(2f, cap = StrokeCap.Round))
        BeomMouth.Open -> {
            drawPath(Path().apply { moveTo(-5f, y - 4f); lineTo(5f, y - 4f); quadraticTo(5f, y + 4f, 0f, y + 4f); quadraticTo(-5f, y + 4f, -5f, y - 4f); close() }, ink)
            drawOval(Tokens.Petal, Offset(-3f, y + 0.5f), Size(6f, 3.5f))
        }
        BeomMouth.O -> drawCircle(ink, 3f, Offset(0f, y))
        BeomMouth.Wobble -> drawPath(Path().apply { moveTo(-6f, y); cubicTo(-3f, y - 3f, -1f, y + 3f, 2f, y); cubicTo(3.5f, y - 2f, 5f, y + 1f, 6f, y) }, ink, style = Stroke(2f, cap = StrokeCap.Round))
        BeomMouth.Flat -> drawLine(ink, Offset(-3f, y), Offset(3f, y), 2f, StrokeCap.Round)
    }
}
