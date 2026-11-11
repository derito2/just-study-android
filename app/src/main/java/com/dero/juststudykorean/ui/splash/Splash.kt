package com.dero.juststudykorean.ui.splash

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.R
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.Poppins
import com.dero.juststudykorean.ui.theme.reduceMotion
import kotlinx.coroutines.delay

/** [titleBias]: where the title sits, -1 top .. 1 bottom (the clear band of each picture). */
internal class SplashArt(@DrawableRes val image: Int, val titleBias: Float)

internal val FlashArts = listOf(
    SplashArt(R.drawable.splash_flash1, 0.5f),
    SplashArt(R.drawable.splash_flash2, 0.12f),
    SplashArt(R.drawable.splash_flash3, 0.12f),
)

/**
 * The flash pictures plus splash_photo_01..11 when the build has them: those stay on the owner's Mac (.gitignore),
 * so they are looked up by name. If resource shrinking is ever turned on, keep them with tools:keep.
 */
@SuppressLint("DiscouragedApi")
private fun arts(context: Context): List<SplashArt> = FlashArts + (1..11)
    .map { context.resources.getIdentifier("splash_photo_%02d".format(it), "drawable", context.packageName) }
    .filter { it != 0 }
    .map { SplashArt(it, 0.44f) }

private const val TotalMs = 1500
private const val FadeFrom = 0.82f // the splash fades out by itself from here (1230 ms)
private val Navy = Color(0xFF00143E)

private fun ramp(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** One picture at random each time the app opens, over the app for a moment: slow zoom, title in, fade out. A tap closes it. */
@Composable
fun SplashOverlay() {
    var visible by rememberSaveable { mutableStateOf(true) } // not again after a rotation
    if (!visible) return
    val context = LocalContext.current
    val art = remember { arts(context).random() }
    val still = reduceMotion()
    val time = remember { Animatable(0f) } // 0..1 over the whole splash, read only in the draw phase
    val exit = remember { Animatable(1f) } // 1 -> 0 after a tap
    var tapped by remember { mutableStateOf(false) }
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
        art,
        Modifier
            .graphicsLayer { alpha = exit.value }
            // Every touch stops here, also during the fade; a tap while it is already fading by itself does nothing.
            .pointerInput(Unit) { detectTapGestures { if (time.value < FadeFrom) tapped = true } },
    ) { if (still) 0.6f else time.value }
}

/** The splash at moment [t] (0..1). */
@Composable
internal fun SplashContent(art: SplashArt, modifier: Modifier = Modifier, t: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .then(modifier)
            .graphicsLayer { alpha = 1f - ramp(t(), FadeFrom, 1f) }
            .background(Navy),
    ) {
        Image(
            painter = painterResource(art.image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { val z = 1f + 0.06f * t(); scaleX = z; scaleY = z },
        )
        Column(
            Modifier.align(BiasAlignment(0f, art.titleBias)).padding(horizontal = 24.dp),
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
                modifier = Modifier.padding(top = 10.dp).graphicsLayer { alpha = ramp(t(), 0.22f, 0.5f) },
            )
        }
    }
}
