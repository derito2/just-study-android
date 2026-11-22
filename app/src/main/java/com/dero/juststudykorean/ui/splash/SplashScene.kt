package com.dero.juststudykorean.ui.splash

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * design/splash.md §3: a scene drawn at the screen's own resolution. Each layer is recorded once in a GraphicsLayer (a drawing list,
 * not a bitmap, so it is traced sharp at any size); every frame only moves them. [t] is 0..1 over the 1500 ms; [still]: the final
 * depth state, with no drift or twinkle.
 */
@Composable
internal fun SplashScene(scene: SceneData, t: () -> Float, still: Boolean, modifier: Modifier = Modifier) {
    key(scene) {
        val layers = scene.layers.map { rememberGraphicsLayer() }
        val recorded = androidx.compose.runtime.remember(scene) { BooleanArray(scene.layers.size) }
        androidx.compose.foundation.layout.Box(
            modifier.fillMaxSize().drawWithCache {
                val cover = max(size.width / SCENE_W, size.height / SCENE_H)
                val origin = Offset((size.width - SCENE_W * cover) / 2f, (size.height - SCENE_H * cover) / 2f)
                // Grain: one dot every 14 × 14 px, fixed seed, at physical resolution (it does not scale).
                val grainBrush = grainBrush(scene.grain)
                onDrawBehind {
                    scene.layers.forEachIndexed { i, layer ->
                        val gl = layers[i]
                        if (!recorded[i]) {
                            // Each layer is recorded at the size of its own content, not the whole screen: far fewer pixels to composite every frame.
                            val b = layerBounds(layer)
                            val left = (origin.x + b.left * cover).coerceIn(0f, size.width).toInt()
                            val top = (origin.y + b.top * cover).coerceIn(0f, size.height).toInt()
                            val right = (origin.x + b.right * cover).coerceIn(0f, size.width).toInt() + 1
                            val bottom = (origin.y + b.bottom * cover).coerceIn(0f, size.height).toInt() + 1
                            val pad = if (layer.blur > 0f) (layer.blur * cover * 3).toInt() else 0 // room for the blur to spread
                            val l = (left - pad).coerceAtLeast(0); val tp = (top - pad).coerceAtLeast(0)
                            val r = (right + pad).coerceAtMost(size.width.toInt()); val bt = (bottom + pad).coerceAtMost(size.height.toInt())
                            gl.topLeft = androidx.compose.ui.unit.IntOffset(l, tp)
                            gl.record(IntSize((r - l).coerceAtLeast(1), (bt - tp).coerceAtLeast(1))) {
                                withTransform({ translate(origin.x - l, origin.y - tp); scale(cover, cover, Offset.Zero) }) { drawScenePaths(layer) }
                            }
                            if (layer.blur > 0f) gl.renderEffect = BlurEffect(layer.blur * cover, layer.blur * cover, TileMode.Decal)
                            recorded[i] = true
                        }
                        val time = if (still) 1f else t()
                        val zoom = 1f + (0.02f + 0.05f * layer.depth) * time
                        val twinkle = if (layer.twinkle && !still) 0.875f + 0.125f * sin(2 * PI * time * 1500 / 900).toFloat() else 1f
                        gl.alpha = (layer.opacity * twinkle).coerceIn(0f, 1f)
                        val drift = if (still) Offset.Zero else layer.drift * (cover * time)
                        withTransform({ scale(zoom, zoom, center); translate(drift.x, drift.y) }) { drawLayer(gl) }
                    }
                    if (scene.grain > 0f) drawRect(grainBrush, size = size)
                }
            },
        ) {}
    }
}

/** The box a layer's paths fill, in scene units (strokes widened by half their width). Gradients that fade out lie inside the paths that carry them. */
private fun layerBounds(layer: SceneLayer): androidx.compose.ui.geometry.Rect {
    var rect: androidx.compose.ui.geometry.Rect? = null
    for (p in layer.paths) {
        val b = p.path.getBounds().let { if (p.stroke != null) it.inflate(p.strokeWidth / 2f) else it }
        rect = rect?.let { r -> androidx.compose.ui.geometry.Rect(minOf(r.left, b.left), minOf(r.top, b.top), maxOf(r.right, b.right), maxOf(r.bottom, b.bottom)) } ?: b
    }
    return rect ?: androidx.compose.ui.geometry.Rect(0f, 0f, SCENE_W, SCENE_H)
}

/**
 * The grain (design/splash.md §3): white and `night-deep` dots of 1 physical px, about one per 14 × 14 px, fixed seed. Drawn as a repeating
 * 126 px tile (an ImageShader), not as ~13 000 separate points every frame: measured on the OPPO, the points cost ~10 ms of frame time.
 */
private fun grainBrush(opacity: Float): androidx.compose.ui.graphics.Brush {
    val tile = androidx.compose.ui.graphics.ImageBitmap(126, 126)
    val canvas = androidx.compose.ui.graphics.Canvas(tile)
    val rnd = Random(7)
    val light = androidx.compose.ui.graphics.Paint().apply { color = Color.White.copy(alpha = opacity) }
    val dark = androidx.compose.ui.graphics.Paint().apply { color = Color(0xFF020B1F).copy(alpha = opacity) }
    for (gy in 0 until 9) for (gx in 0 until 9) {
        val x = gx * 14f + rnd.nextInt(14)
        val y = gy * 14f + rnd.nextInt(14)
        canvas.drawRect(x, y, x + 1f, y + 1f, if (rnd.nextBoolean()) light else dark)
    }
    return androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

private fun DrawScope.drawScenePaths(layer: SceneLayer) {
    for (p in layer.paths) {
        when (val f = p.fill) {
            is SceneFill.Flat -> drawPath(p.path, f.color, alpha = p.fillOpacity)
            is SceneFill.Gradient -> drawPath(p.path, f.brush, alpha = p.fillOpacity)
            null -> Unit
        }
        val s = p.stroke ?: continue
        val style = Stroke(p.strokeWidth, cap = p.cap, join = p.join)
        when (s) {
            is SceneFill.Flat -> drawPath(p.path, s.color, alpha = p.strokeOpacity, style = style)
            is SceneFill.Gradient -> drawPath(p.path, s.brush, alpha = p.strokeOpacity, style = style)
        }
    }
}
