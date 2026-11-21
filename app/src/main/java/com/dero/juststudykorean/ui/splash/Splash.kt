package com.dero.juststudykorean.ui.splash

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.Poppins
import com.dero.juststudykorean.ui.theme.reduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** One scene from assets/splash/ at random; a broken file is skipped for another. null if none can be read (plain `night` with the text). */
internal fun loadScene(context: Context): SceneData? {
    val files = context.assets.list("splash").orEmpty().filter { it.endsWith(".svg") }.shuffled()
    for (name in files) readScene(context.assets.open("splash/$name"))?.let { return it }
    return null
}

private const val TotalMs = 1500
private const val FadeFrom = 0.82f // the splash fades out by itself from here (1230 ms)
private const val SceneFadeMs = 120

private fun ramp(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** One scene at random each time the app opens, over the app for a moment: parallax, title in, fade out. A tap closes it. */
@Composable
fun SplashOverlay() {
    var visible by rememberSaveable { mutableStateOf(true) } // not again after a rotation
    if (!visible) return
    val context = LocalContext.current
    var scene by remember { mutableStateOf<SceneData?>(null) }
    val sceneAlpha = remember { Animatable(0f) }
    val still = reduceMotion()
    val time = remember { Animatable(0f) } // 0..1 over the whole splash, read only in the draw phase
    val exit = remember { Animatable(1f) } // 1 -> 0 after a tap
    var tapped by remember { mutableStateOf(false) }
    // Read off the main thread; meanwhile the plain `night` shows, and the scene comes in with a 120 ms fade. The title does not wait.
    LaunchedEffect(Unit) {
        scene = withContext(Dispatchers.Default) { loadScene(context) }
        if (scene != null) { if (still) sceneAlpha.snapTo(1f) else sceneAlpha.animateTo(1f, tween(SceneFadeMs)) }
    }
    LaunchedEffect(Unit) {
        // "Remove animations" ends animateTo at once: hold the still picture for the same time instead.
        if (still) delay(TotalMs.toLong()) else time.animateTo(1f, tween(TotalMs, easing = LinearEasing))
        visible = false
    }
    LaunchedEffect(tapped) {
        if (!tapped) return@LaunchedEffect
        exit.animateTo(0f, tween(Motion.Fast, easing = Motion.EaseOut)) // at once with "Remove animations"
        visible = false
    }
    SplashContent(
        scene, still,
        Modifier
            .graphicsLayer { alpha = exit.value }
            // Every touch stops here, also during the fade; a tap while it is already fading by itself does nothing.
            .pointerInput(Unit) { detectTapGestures { if (time.value < FadeFrom) tapped = true } },
        sceneAlpha = { sceneAlpha.value },
    ) { if (still) 0.6f else time.value }
}

/** The splash at moment [t] (0..1). [scene] null: only `night` and the text. */
@Composable
internal fun SplashContent(scene: SceneData?, still: Boolean = false, modifier: Modifier = Modifier, sceneAlpha: () -> Float = { 1f }, t: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .then(modifier)
            .graphicsLayer { alpha = 1f - ramp(t(), FadeFrom, 1f) }
            .background(com.dero.juststudykorean.ui.theme.Tokens.Night),
    ) {
        if (scene != null) SplashScene(scene, t, still, Modifier.graphicsLayer { alpha = sceneAlpha() })
        Column(
            Modifier.align(BiasAlignment(0f, 2f * (scene?.titleY ?: 0.5f) - 1f)).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Just Study Korean",
                color = Color.White,
                fontFamily = Poppins,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    val a = ramp(t(), 0.05f, 0.32f)
                    alpha = a
                    translationY = (1f - a) * 14.dp.toPx()
                },
            )
            Text(
                "by DERO",
                color = Color(0xFFBFD9FF),
                fontFamily = Poppins,
                fontSize = 16.sp,
                letterSpacing = 3.sp,
                // 180–520 ms: the subtitle used to enter at 330–750 and was nearly invisible in the ~0.5 s captures.
                modifier = Modifier.padding(top = 10.dp).graphicsLayer { alpha = ramp(t(), 0.12f, 0.3467f) },
            )
        }
    }
}
