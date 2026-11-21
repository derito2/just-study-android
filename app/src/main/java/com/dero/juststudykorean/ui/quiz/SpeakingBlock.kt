package com.dero.juststudykorean.ui.quiz

import com.dero.juststudykorean.ui.components.PillTone
import com.dero.juststudykorean.ui.components.Pill
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.korean.KOREAN_ALTERNATIVES
import com.dero.juststudykorean.korean.speech.SpokenComparison
import com.dero.juststudykorean.ui.components.KoSide
import com.dero.juststudykorean.ui.components.softBox
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.components.StickerSize
import com.dero.juststudykorean.ui.components.FileName
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.dero.juststudykorean.ui.theme.Tokens
import com.dero.juststudykorean.ui.theme.reduceMotion

/** What the spoken block can do (QuestionScreen wires them to the ViewModel and the permission). */
data class SpeakActions(
    val onMic: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onTryAgain: () -> Unit = {},
    val onShowAnswer: () -> Unit = {},
    val onListen: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** The English meaning of a write_ko question ("Write in Korean: \"Apple\"" → Apple). */
fun meaningOf(q: QuestionsEntity): String = Regex("^Write in Korean: \"(.*)\"$").find(q.prompt)?.groupValues?.get(1) ?: q.prompt

/** design/quiz-speaking.md B: the card's top row, the question and the spoken block (instead of the answer field). */
@Composable
fun SpokenQuestion(q: QuestionsEntity, document: String?, speaking: SpeakingUi, actions: SpeakActions, voiceReady: Boolean, permissionNeeded: Boolean) {
    val c = JustStudyTheme.colors
    val ink = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The title and the chip take what they need; the document's name gets the rest and shortens (on the phone, with a bigger
        // font, "PRACTICE" used to break in two next to a name like "Anki_vocabulary_1").
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Say it in Korean", Modifier.alignByBaseline(), style = MaterialTheme.typography.bodySmall, color = c.muted)
            KoSide("한국어로 말하기", 12.sp, Modifier.alignByBaseline(), color = c.muted)
            Pill("PRACTICE", PillTone.Info, Modifier.padding(start = Tokens.Space2))
        }
        document?.let {
            FileName(
                it, Modifier.weight(1f).padding(start = Tokens.Space2), style = MaterialTheme.typography.bodySmall, color = c.muted,
                maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        } ?: Spacer(Modifier.weight(1f))
    }
    val answer = q.correctAnswer.split(KOREAN_ALTERNATIVES).first().trim()
    if (readAloud(q)) {
        Text(answer, style = JustStudyTheme.korean.koPrompt, color = ink)
        Text(meaningOf(q), style = MaterialTheme.typography.bodyMedium, color = c.muted)
        Text("Read it out loud.", style = MaterialTheme.typography.bodySmall, color = c.muted)
    } else {
        Text(meaningOf(q), style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = ink)
        Text("Say it out loud, in Korean.", style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
    SpeakingBlock(speaking, actions, permissionNeeded)
    val shown = speaking.comparison
    if (shown != null || speaking.shownAnswer) Comparison(shown, if (shown == null) q.correctAnswer else null, voiceReady, actions.onListen)
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space4)) {
        if (speaking.phase == SpeakPhase.Compared) TextAction("Try again", c.brandText, actions.onTryAgain)
        if (speaking.attempts == 0 && !speaking.shownAnswer && speaking.phase in setOf(SpeakPhase.Ready, SpeakPhase.NoSpeech)) StickerButton("Show answer", actions.onShowAnswer, ko = "답 보기", color = StickerColor.Gray, size = StickerSize.Small)
    }
}

@Composable
private fun TextAction(text: String, color: Color, onClick: () -> Unit) =
    Text(text, Modifier.heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 14.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)

/** MicButton, LevelWave, the status line and the fixed note (design B, C and D). */
@Composable
fun SpeakingBlock(s: SpeakingUi, actions: SpeakActions, permissionNeeded: Boolean) {
    val c = JustStudyTheme.colors
    val still = LocalFrozenMotion.current || reduceMotion()
    val blocked = s.phase in setOf(SpeakPhase.NotDownloaded, SpeakPhase.Unavailable, SpeakPhase.PermissionDenied)
    Column(Modifier.fillMaxWidth().padding(vertical = Tokens.Space4), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
            LevelWave(s, still, mirrored = true)
            MicButton(s, still, blocked, actions.onMic)
            LevelWave(s, still, mirrored = false)
        }
        val (line, ko, color) = when (s.phase) {
            SpeakPhase.Ready -> Triple("Tap and speak", "누르고 말하세요", MaterialTheme.colorScheme.onSurface)
            SpeakPhase.Listening -> Triple("Listening…", "듣고 있어요", c.danger)
            SpeakPhase.Processing -> Triple("Checking…", "확인 중", c.muted)
            SpeakPhase.Compared -> Triple(if (s.comparison?.matches == true) "" else "Compare below", null, MaterialTheme.colorScheme.onSurface)
            SpeakPhase.NoSpeech -> Triple("Didn't catch that. Tap and try again", "못 들었어요", MaterialTheme.colorScheme.onSurface)
            SpeakPhase.NotDownloaded -> Triple("Korean speech isn't downloaded on this phone", null, c.muted)
            SpeakPhase.Unavailable -> Triple("Speech isn't working right now", null, c.muted)
            SpeakPhase.PermissionDenied -> Triple("Microphone is off for this app", null, c.muted)
        }
        val announce = s.comparison?.takeIf { s.phase == SpeakPhase.Compared }?.let { "We heard ${it.heard}. Your notes ${it.notes}." + if (it.matches) " Matches your notes." else "" } ?: line
        Row(
            Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = announce },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space3),
        ) {
            if (line.isNotEmpty()) Row(verticalAlignment = Alignment.Bottom) {
                Text(line, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center)
                ko?.let { KoSide(it, 12.sp, color = color) }
            }
            if (s.phase == SpeakPhase.Listening) TextAction("Cancel", c.muted, actions.onCancel)
        }
        s.partial?.takeIf { s.phase == SpeakPhase.Listening }?.let { Text(it, style = JustStudyTheme.korean.koPrompt, color = c.muted) }
        when {
            s.phase == SpeakPhase.NotDownloaded -> Text("Download it in Android's speech settings, then come back.", fontSize = 12.sp, color = c.muted, textAlign = TextAlign.Center)
            s.phase == SpeakPhase.PermissionDenied -> Text("Turn it on in Android settings to practice out loud.", fontSize = 12.sp, color = c.muted, textAlign = TextAlign.Center)
            s.phase == SpeakPhase.Unavailable -> StickerButton("Try again", actions.onRetry, color = StickerColor.Gray, size = StickerSize.Small)
            permissionNeeded && s.phase == SpeakPhase.Ready -> Text("The microphone is used only while you answer. Nothing is recorded or sent.", fontSize = 12.sp, color = c.muted, textAlign = TextAlign.Center)
        }
        Text("Practice only. Your phone's speech recognition can mishear you, so this isn't scored.", fontSize = 12.sp, color = c.muted, textAlign = TextAlign.Center)
    }
}

/** 72 dp, action (danger while listening); halo while listening, a 270° arc while checking (design D). */
@Composable
private fun MicButton(s: SpeakingUi, still: Boolean, blocked: Boolean, onMic: () -> Unit) {
    val c = JustStudyTheme.colors
    val listening = s.phase == SpeakPhase.Listening
    val processing = s.phase == SpeakPhase.Processing
    val fill = when { blocked -> c.disabledFill; listening -> c.danger; else -> c.action }
    val edge = when { blocked -> c.disabledFill; listening -> c.dangerEdge; else -> c.actionEdge }
    val t = if (still) null else rememberInfiniteTransition(label = "mic")
    val halo = t?.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "halo")
    val spin = t?.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spin")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(s.phase) {
        if (still) return@LaunchedEffect
        when (s.phase) {
            SpeakPhase.Listening -> { pop.animateTo(1.08f, tween(120)); pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) }
            SpeakPhase.NoSpeech -> { pop.animateTo(1.04f, tween(200)); pop.animateTo(1f, tween(200)) }
            else -> Unit
        }
    }
    Box(
        Modifier.size(128.dp).drawBehind {
            if (listening) {
                val ring = 2.dp.toPx()
                for (k in 0..1) { // two rings, 600 ms apart (D); frozen: the first frame
                    val p = ((halo?.value ?: 0.25f) + k * 0.5f) % 1f
                    drawCircle(c.danger.copy(alpha = 0.5f * (1 - p)), (36 + 28 * p).dp.toPx(), style = Stroke(ring))
                }
            }
            if (processing) drawArc(c.brand, spin?.value ?: 0f, 270f, false, Offset(size.width / 2 - 40.dp.toPx(), size.height / 2 - 40.dp.toPx()), Size(80.dp.toPx(), 80.dp.toPx()), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(72.dp)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = if (processing) 0.6f else 1f }
                .drawBehind { drawCircle(edge, size.minDimension / 2, Offset(size.width / 2, size.height / 2 + 4.dp.toPx())) }
                .background(fill, CircleShape)
                .clickable(enabled = !blocked && !processing, onClick = onMic)
                .semantics { role = Role.Button; contentDescription = if (listening) "Stop listening" else "Practice out loud" },
            contentAlignment = Alignment.Center,
        ) {
            LucideIcon(Lucide.Mic, contentDescription = null, size = 28.dp, tint = Color.White)
            if (s.phase == SpeakPhase.PermissionDenied) Box(Modifier.size(32.dp).drawBehind { // the mic crossed out
                drawLine(Color.White, Offset(0f, 0f), Offset(size.width, size.height), 2.5.dp.toPx(), StrokeCap.Round)
            })
        }
    }
}

/** 4 bars on each side, 6–36 dp, following the microphone level (spring 300, 40 ms per bar outwards). */
@Composable
private fun LevelWave(s: SpeakingUi, still: Boolean, mirrored: Boolean) {
    val c = JustStudyTheme.colors
    val listening = s.phase == SpeakPhase.Listening
    val target = if (listening) (if (still) 0.5f else s.level) else 0f
    val bars = (0 until 4).map { i ->
        animateFloatAsState(target * (1f - i * 0.15f), if (still) tween(0) else spring(stiffness = 300f), label = "bar").value
    }
    Box(Modifier.width(28.dp).heightIn(min = 36.dp).drawBehind {
        for (i in 0 until 4) {
            val h = (6 + 30 * bars[i]).dp.toPx()
            val x = if (mirrored) size.width - (i * 8 + 4).dp.toPx() else (i * 8).dp.toPx()
            drawRoundRect(c.danger.copy(alpha = if (listening) 1f else 0.25f), Offset(x, (size.height - h) / 2), Size(4.dp.toPx(), h), CornerRadius(2.dp.toPx()))
        }
    })
}

/** We heard / Your notes, the warn underlines, Matches your notes and Listen (design B). [onlyNotes]: Show answer. */
@Composable
private fun Comparison(comparison: SpokenComparison?, onlyNotes: String?, voiceReady: Boolean, onListen: () -> Unit) {
    val c = JustStudyTheme.colors
    val still = LocalFrozenMotion.current || reduceMotion()
    val enter = remember(comparison) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(comparison) { if (!still) enter.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = enter.value; translationY = (1 - enter.value) * 8.dp.toPx() }, verticalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
        if (comparison?.matches == true) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
            LucideIcon(Lucide.Check, contentDescription = null, size = 18.dp, tint = c.correctInk)
            Text("Matches your notes", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.correctInk)
        }
        Column(
            Modifier.fillMaxWidth().softBox().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space1),
        ) {
            if (comparison != null) {
                Text("We heard", fontSize = 12.sp, color = c.muted)
                Marked(comparison.heard, comparison.heardMarks, enter.value)
            }
            Text("Your notes", fontSize = 12.sp, color = c.muted)
            Marked(comparison?.notes ?: onlyNotes.orEmpty(), comparison?.notesMarks.orEmpty(), enter.value)
            if (voiceReady) StickerButton("Listen", onListen, Modifier.padding(top = Tokens.Space1), ko = "듣기", color = StickerColor.Gray, size = StickerSize.Small, icon = Lucide.Volume2)
        }
    }
}

/** Korean text with the syllables at [marks] underlined in warn (2 dp), drawn left to right as [progress] goes 0 → 1. */
@Composable
private fun Marked(text: String, marks: List<Int>, progress: Float) {
    val warn = JustStudyTheme.colors.warn
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text, style = JustStudyTheme.korean.koPrompt, color = MaterialTheme.colorScheme.onSurface, onTextLayout = { layout = it },
        modifier = Modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            val limit = size.width * progress
            for (i in marks) {
                if (i >= text.length) continue
                val box = l.getBoundingBox(i)
                if (box.left > limit) continue
                val y = box.bottom + 1.dp.toPx()
                drawLine(warn, Offset(box.left, y), Offset(minOf(box.right, limit), y), 2.dp.toPx())
            }
        },
    )
}
