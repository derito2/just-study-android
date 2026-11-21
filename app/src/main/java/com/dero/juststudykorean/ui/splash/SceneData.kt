package com.dero.juststudykorean.ui.splash

import android.util.Xml
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.PathParser
import java.io.InputStream
import org.xmlpull.v1.XmlPullParser

/** design/splash.md §2: the subset of SVG a launch scene uses. A gradient or a flat colour, in canvas units (viewBox 1080 × 2400). */
internal sealed interface SceneFill {
    data class Flat(val color: Color) : SceneFill
    class Gradient(val brush: Brush) : SceneFill
}

internal class ScenePath(
    val path: Path,
    val fill: SceneFill?,
    val fillOpacity: Float,
    val stroke: SceneFill?,
    val strokeWidth: Float,
    val strokeOpacity: Float,
    val cap: StrokeCap,
    val join: StrokeJoin,
)

internal class SceneLayer(
    val id: String,
    val depth: Float,
    val opacity: Float,
    val blur: Float,
    val twinkle: Boolean,
    val drift: Offset,
    val paths: List<ScenePath>,
)

internal class SceneData(val layers: List<SceneLayer>, val titleY: Float, val grain: Float)

internal const val SCENE_W = 1080f
internal const val SCENE_H = 2400f

/** Reads one scene. Returns null if the file is not a readable scene (broken XML, no viewBox, no layers): never throws. */
internal fun readScene(input: InputStream): SceneData? = try {
    input.use { parse(it) }
} catch (_: Exception) {
    null
}

private fun parse(input: InputStream): SceneData? {
    val xml = Xml.newPullParser()
    xml.setInput(input, null)
    val gradients = HashMap<String, SceneFill>()
    val layers = ArrayList<SceneLayer>()
    var titleY = 0.74f
    var grain = 0.035f

    // The gradient being read.
    var gradId: String? = null
    var gradRadial = false
    var gradAttrs: Map<String, String> = emptyMap()
    val stops = ArrayList<Pair<Float, Color>>()
    // The layer being read.
    var layerAttrs: Map<String, String>? = null
    val paths = ArrayList<ScenePath>()

    fun attrs() = (0 until xml.attributeCount).associate { xml.getAttributeName(it) to xml.getAttributeValue(it) }
    fun num(m: Map<String, String>, key: String) = m[key]?.toFloatOrNull() ?: 0f

    fun fillOf(spec: String?): SceneFill? = when {
        spec == null || spec == "none" -> null
        spec.startsWith("url(#") -> gradients[spec.removePrefix("url(#").removeSuffix(")")]
        spec.startsWith("#") -> SceneFill.Flat(Color(android.graphics.Color.parseColor(spec)))
        else -> null
    }

    var sawSvg = false
    while (true) {
        when (xml.next()) {
            XmlPullParser.START_TAG -> {
                val a = attrs()
                when (xml.name) {
                    "svg" -> {
                        val box = a["viewBox"]?.trim()?.split(Regex("[ ,]+"))?.map { it.toFloat() }
                        if (box == null || box.size != 4 || box[2] != SCENE_W || box[3] != SCENE_H) return null
                        titleY = a["data-title-y"]?.toFloatOrNull() ?: titleY
                        grain = (a["data-grain"]?.toFloatOrNull() ?: grain).coerceIn(0f, 0.06f)
                        sawSvg = true
                    }
                    "linearGradient", "radialGradient" -> { gradId = a["id"]; gradRadial = xml.name == "radialGradient"; gradAttrs = a; stops.clear() }
                    "stop" -> {
                        val c = Color(android.graphics.Color.parseColor(a["stop-color"] ?: "#000000"))
                        stops += (a["offset"]?.toFloatOrNull() ?: 0f) to c.copy(alpha = a["stop-opacity"]?.toFloatOrNull() ?: 1f)
                    }
                    "g" -> { layerAttrs = a; paths.clear() }
                    "path" -> if (layerAttrs != null) {
                        val d = a["d"] ?: return null
                        val path = PathParser().parsePathString(d).toPath()
                        if (a["fill-rule"] == "evenodd") path.fillType = PathFillType.EvenOdd
                        paths += ScenePath(
                            path, fillOf(a["fill"] ?: "#000000"), a["fill-opacity"]?.toFloatOrNull() ?: 1f,
                            fillOf(a["stroke"]), a["stroke-width"]?.toFloatOrNull() ?: 1f, a["stroke-opacity"]?.toFloatOrNull() ?: 1f,
                            when (a["stroke-linecap"]) { "round" -> StrokeCap.Round; "square" -> StrokeCap.Square; else -> StrokeCap.Butt },
                            when (a["stroke-linejoin"]) { "round" -> StrokeJoin.Round; "bevel" -> StrokeJoin.Bevel; else -> StrokeJoin.Miter },
                        )
                    }
                }
            }
            XmlPullParser.END_TAG -> when (xml.name) {
                "linearGradient", "radialGradient" -> {
                    val id = gradId
                    if (id != null && stops.isNotEmpty()) {
                        val colorStops = stops.map { it.first to it.second }.toTypedArray()
                        gradients[id] = SceneFill.Gradient(
                            if (gradRadial) Brush.radialGradient(*colorStops, center = Offset(num(gradAttrs, "cx"), num(gradAttrs, "cy")), radius = num(gradAttrs, "r").coerceAtLeast(1f))
                            else Brush.linearGradient(*colorStops, start = Offset(num(gradAttrs, "x1"), num(gradAttrs, "y1")), end = Offset(num(gradAttrs, "x2"), num(gradAttrs, "y2"))),
                        )
                    }
                    gradId = null
                }
                "g" -> layerAttrs?.let { g ->
                    val drift = g["data-drift"]?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }?.takeIf { it.size == 2 }?.let { Offset(it[0], it[1]) } ?: Offset.Zero
                    layers += SceneLayer(
                        g["id"].orEmpty(), (g["data-depth"]?.toFloatOrNull() ?: 0f).coerceIn(0f, 1f), g["opacity"]?.toFloatOrNull() ?: 1f,
                        (g["data-blur"]?.toFloatOrNull() ?: 0f).coerceIn(0f, 12f), g["data-twinkle"] == "1", drift, paths.toList(),
                    )
                    layerAttrs = null
                }
            }
            XmlPullParser.END_DOCUMENT -> return if (sawSvg && layers.isNotEmpty()) SceneData(layers, titleY, grain) else null
        }
    }
}
