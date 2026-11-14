package com.dero.juststudykorean.ui.games.pieces

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.data.audio.KoreanVoice
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.dero.juststudykorean.ui.theme.reduceMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** The Korean voice of Games (null in screenshot tests: nothing sounds). */
val LocalKoreanVoice = staticCompositionLocalOf<KoreanVoice?> { null }

/**
 * Plays the listening games' audio: a sequence with pauses; [current] is the one sounding (-1: none). With the media
 * volume at 0 it waits ([waitingForVolume]) instead of sounding: no round of audio without audio.
 */
class Speaker(private val voice: KoreanVoice?) {
    var speaking by mutableStateOf(false); private set
    var current by mutableIntStateOf(-1); private set
    var waitingForVolume by mutableStateOf(false); private set

    /** Something was cut while sounding (a pause): the round must start again. */
    var interrupted = false; private set

    suspend fun play(texts: List<String>, rate: Float = 1f, pauseMs: Int = 0) {
        speaking = true
        try {
            while (voice?.muted == true) { waitingForVolume = true; delay(500) }
            waitingForVolume = false
            texts.forEachIndexed { i, t ->
                if (i > 0) delay(pauseMs.toLong())
                current = i
                voice?.speak(t, rate) ?: delay(600)
            }
        } catch (e: CancellationException) {
            interrupted = true
            throw e
        } finally { current = -1; speaking = false; waitingForVolume = false }
    }

    fun interrupt() { interrupted = true; voice?.stop() }

    fun stop() = voice?.stop()
}

/**
 * games.md 9: an audio cut by a pause can't be resumed fairly; the round starts again with the same content. The
 * content restarts (a new [Speaker]) on resume, only if the pause came while something sounded.
 */
@Composable
fun Restartable(paused: Boolean, content: @Composable (Speaker) -> Unit) {
    val voice = LocalKoreanVoice.current
    var generation by remember { mutableIntStateOf(0) }
    val speaker = remember(generation) { Speaker(voice) }
    LaunchedEffect(paused) {
        if (paused) { if (speaker.speaking) speaker.interrupt() } else if (speaker.interrupted) generation++
    }
    key(generation) { content(speaker) }
}

/** The pill over the button when the volume is 0 (games-minigames.md, Audio). */
@Composable
fun VolumeUpPill(speaker: Speaker) {
    if (!speaker.waitingForVolume) return
    val c = JustStudyTheme.colors
    Text("Turn the volume up to play.", fontSize = 12.sp, color = c.fillWarn, modifier = Modifier.background(c.warnFill, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
private fun InfiniteTransition.wave(period: Int, offset: Int) = animateFloat(
    0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart, initialStartOffset = androidx.compose.animation.core.StartOffset(offset)), label = "wave",
)

/** ReplayButton (56 dp, action, volume-2): rings while it sounds; dots below, one per replay left. */
@Composable
fun ReplayButton(speaking: Boolean, left: Int, total: Int, onClick: () -> Unit, enabled: Boolean = true) {
    val c = JustStudyTheme.colors
    val frozen = LocalFrozenMotion.current || reduceMotion()
    val can = enabled && left > 0 && !speaking
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            if (speaking && !frozen) {
                val t = rememberInfiniteTransition(label = "rings")
                val rings = listOf(t.wave(900, 0), t.wave(900, 300), t.wave(900, 600))
                val beat = t.animateFloat(1f, 1.08f, infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "beat")
                Box(Modifier.size(56.dp).drawBehind {
                    rings.forEach { r -> val p = r.value; drawCircle(c.brand.copy(alpha = 0.6f * (1 - p)), (28 + 24 * p).dp.toPx(), style = Stroke(2.dp.toPx())) }
                })
                ReplayFace(can, speaking, onClick, left, Modifier.graphicsLayer { scaleX = beat.value; scaleY = beat.value })
            } else ReplayFace(can, speaking, onClick, left, Modifier)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(total) { Box(Modifier.size(6.dp).background(if (it < left) c.brand else c.field, CircleShape)) }
        }
    }
}

@Composable
private fun ReplayFace(can: Boolean, speaking: Boolean, onClick: () -> Unit, left: Int, modifier: Modifier) {
    val c = JustStudyTheme.colors
    val frozen = LocalFrozenMotion.current || reduceMotion()
    val fill = when { speaking && frozen -> c.brand; can || speaking -> c.action; else -> c.disabledFill }
    Box(
        modifier.size(56.dp).background(fill, CircleShape)
            .selectable(false, enabled = can, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Play the audio again, $left left" },
        contentAlignment = Alignment.Center,
    ) { LucideIcon(Lucide.Volume2, contentDescription = null, size = 24.dp, tint = Color.White) }
}

/** VoiceBars: 5 bars of 4 dp that move while it sounds (not the real wave: a "sounding" sign). */
@Composable
fun VoiceBars(active: Boolean, color: Color = JustStudyTheme.colors.brand, modifier: Modifier = Modifier) {
    val frozen = LocalFrozenMotion.current || reduceMotion()
    val heights: List<State<Float>> = if (active && !frozen) {
        val t = rememberInfiniteTransition(label = "bars")
        listOf(310, 370, 430, 290, 350).map { t.animateFloat(6f, 28f, infiniteRepeatable(tween(it, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bar") }
    } else List(5) { remember { mutableStateOf(6f) } }
    Box(modifier.width(36.dp).size(36.dp, 28.dp).drawBehind {
        heights.forEachIndexed { i, h ->
            val height = h.value.dp.toPx()
            drawRoundRect(color, Offset(i * 8.dp.toPx(), (size.height - height) / 2), Size(4.dp.toPx(), height), CornerRadius(2.dp.toPx()))
        }
    })
}
