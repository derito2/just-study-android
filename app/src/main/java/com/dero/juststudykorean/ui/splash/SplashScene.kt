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
                val grain = grainPoints(size.width, size.height)
                onDrawBehind {
                    scene.layers.forEachIndexed { i, layer ->
                        val gl = layers[i]
                        if (!recorded[i]) {
                            gl.record(IntSize(size.width.toInt(), size.height.toInt())) {
                                withTransform({ translate(origin.x, origin.y); scale(cover, cover, Offset.Zero) }) { drawScenePaths(layer) }
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
                    if (scene.grain > 0f) {
                        drawPoints(grain.light, PointMode.Points, Color.White.copy(alpha = scene.grain), 1f, blendMode = BlendMode.SrcOver)
                        drawPoints(grain.dark, PointMode.Points, Color(0xFF020B1F).copy(alpha = scene.grain), 1f, blendMode = BlendMode.SrcOver)
                    }
                }
            },
        ) {}
    }
}

private class Grain(val light: List<Offset>, val dark: List<Offset>)

private fun grainPoints(w: Float, h: Float): Grain {
    val rnd = Random(7)
    val light = ArrayList<Offset>()
    val dark = ArrayList<Offset>()
    var y = 0f
    while (y < h) {
        var x = 0f
        while (x < w) {
            val p = Offset(x + rnd.nextFloat() * 14f, y + rnd.nextFloat() * 14f)
            if (rnd.nextBoolean()) light += p else dark += p
            x += 14f
        }
        y += 14f
    }
    return Grain(light, dark)
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
