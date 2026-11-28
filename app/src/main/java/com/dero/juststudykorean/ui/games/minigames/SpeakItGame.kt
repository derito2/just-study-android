package com.dero.juststudykorean.ui.games.minigames

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.data.speech.KoreanSpeech
import com.dero.juststudykorean.data.speech.SpeechAvailability
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.components.StickerSize
import com.dero.juststudykorean.ui.components.runCatchingCancellable
import com.dero.juststudykorean.ui.components.speech.Comparison
import com.dero.juststudykorean.ui.components.speech.SpeakActions
import com.dero.juststudykorean.ui.components.speech.SpeakPhase
import com.dero.juststudykorean.ui.components.speech.SpeakingBlock
import com.dero.juststudykorean.ui.components.speech.SpeakingMachine
import com.dero.juststudykorean.ui.components.speech.findActivity
import com.dero.juststudykorean.ui.games.LocalSpeakWord
import com.dero.juststudykorean.ui.games.pieces.LocalKoreanVoice
import com.dero.juststudykorean.ui.games.stage.StageEvent
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The recognizer for Speak It, and what Games does when you refuse the microphone (it leaves the draw) or grant it. */
class GamesSpeech(val speech: KoreanSpeech?, val onDenied: () -> Unit = {}, val onGranted: () -> Unit = {})

val LocalGamesSpeech = staticCompositionLocalOf { GamesSpeech(null) }

/**
 * Speak It (design/beta.md 7, F7): the English on top and the common microphone block (Listening's shadowing). If it
 * matches: celebrated and the word sounds. If not: what the phone heard against your notes, with Try again and Skip;
 * never "Not quite" (D9-6). The round is always neutral and only counts as seen (D11-8). The microphone is asked when
 * tapped (D11-6); nothing heard is stored or logged.
 */
@Composable
fun SpeakItGame(spec: RoundSpec.SpeakIt, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope) {
    val games = LocalGamesSpeech.current
    val speech = games.speech
    val scope = rememberCoroutineScope()
    val machine = remember(spec) { speech?.let { SpeakingMachine(it, scope) } }
    val ui = machine?.state?.collectAsStateWithLifecycle()?.value
    val voice = LocalKoreanVoice.current
    val speakWord = LocalSpeakWord.current.on
    var done by remember(spec) { mutableStateOf(false) }
    val started = remember(spec) { System.currentTimeMillis() }
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) { games.onGranted(); machine?.tap() }
        else if (context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) != true) { games.onDenied(); machine?.permissionDenied() }
    }
    LaunchedEffect(spec) {
        stage.clear()
        val a = speech?.let { runCatchingCancellable { it.availability() }.getOrNull() } ?: SpeechAvailability.Unsupported
        machine?.start(spec.item.korean, sentence = false, availability = a, permissionDenied = false)
    }
    LaunchedEffect(paused) { if (paused) machine?.close() }
    DisposableEffect(machine) { onDispose { machine?.close() } }
    fun finish(skipped: Boolean) {
        if (done) return
        done = true
        machine?.close()
        val tries = ui?.attempts ?: 0
        onResult(RoundResult(Outcome.NEUTRAL, (tries - if (skipped) 0 else 1).coerceAtLeast(0), System.currentTimeMillis() - started, spec.targets, if (skipped) spec.targets else emptyList()))
    }
    val matched = ui?.phase == SpeakPhase.Compared && ui.comparison?.matches == true
    LaunchedEffect(matched) {
        if (!matched) return@LaunchedEffect
        stage.send(StageEvent.RoundWon) // celebrated, though never graded
        if (speakWord) runCatching { voice?.speak(spec.item.korean) }
        delay(900)
        finish(skipped = false)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(spec.item.meaning, style = JustStudyTheme.korean.koPrompt, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, maxLines = 2)
        if (ui != null) {
            SpeakingBlock(
                ui,
                SpeakActions(onMic = { if (paused || done) Unit else if (granted) machine.tap() else ask.launch(Manifest.permission.RECORD_AUDIO) }, onCancel = { machine.close() }),
                permissionNeeded = !granted,
            )
            if (ui.phase == SpeakPhase.Compared && !matched) Comparison(ui.comparison, null, voice != null, onListen = { scope.launch { runCatching { voice?.speak(spec.item.korean) } } }, showMatch = false)
        }
        if (!matched) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui?.phase == SpeakPhase.Compared || ui?.phase == SpeakPhase.NoSpeech) StickerButton("Try again", { machine?.tryAgain() }, color = StickerColor.Gray, size = StickerSize.Small)
            StickerButton("Skip", { finish(skipped = true) }, color = StickerColor.Gray, size = StickerSize.Small, enabled = ui?.listening != true)
        }
    }
}

