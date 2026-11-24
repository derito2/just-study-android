package com.dero.juststudykorean.ui.listening

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.korean.hangul.HangulIme
import com.dero.juststudykorean.korean.listening.ListenMode
import com.dero.juststudykorean.ui.components.BannerTone
import com.dero.juststudykorean.ui.components.EmptyState
import com.dero.juststudykorean.ui.components.JsTextField
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.LoadingState
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.ResultBanner
import com.dero.juststudykorean.ui.components.SelectedGroup
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.components.StickerSize
import com.dero.juststudykorean.ui.components.TypedResult
import com.dero.juststudykorean.ui.components.softBox
import com.dero.juststudykorean.ui.components.speech.Comparison
import com.dero.juststudykorean.ui.components.speech.SpeakActions
import com.dero.juststudykorean.ui.components.speech.SpeakingBlock
import com.dero.juststudykorean.ui.components.speech.SpeakingUi
import com.dero.juststudykorean.ui.components.speech.findActivity
import com.dero.juststudykorean.ui.games.pieces.HangulKeyboard
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

private val RATES = listOf(0.7f, 1f, 1.2f)

@Composable
fun ListeningScreen(onBack: () -> Unit, vm: ListeningViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.open() }
    val ui by vm.state.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    val system by vm.systemKeyboard.collectAsStateWithLifecycle()
    // The microphone permission, asked only when the microphone is tapped (D9-1, D11-6), as in the Quiz.
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) vm.tapMic() else if (context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) != true) vm.micDenied()
    }
    // Hands-free keeps the screen on while it goes; leaving or locking pauses it and closes the microphone.
    val view = LocalView.current
    DisposableEffect(ui.running) { view.keepScreenOn = ui.running; onDispose { view.keepScreenOn = false } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) { vm.pause(); vm.closeMic() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); vm.pause(); vm.closeMic() }
    }
    ListeningContent(
        ui, speaking, system, voiceReady = ui.reason == null, permissionNeeded = !granted, onBack = onBack, onMode = vm::setMode, onRate = vm::setRate,
        onPlay = vm::play, onReplay = vm::replay, onSlower = vm::slower, onType = vm::type, onCheck = vm::check, onNext = vm::next, onPause = vm::pause,
        onSystemKeyboard = { vm.setSystemKeyboard(it) },
        speakActions = SpeakActions(
            onMic = { if (granted) vm.tapMic() else ask.launch(Manifest.permission.RECORD_AUDIO) }, onCancel = { vm.cancelMic() },
            onTryAgain = { vm.tryAgain() }, onListen = vm::replay, onRetry = { vm.retrySpeech() },
        ),
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().border(Tokens.Border, JustStudyTheme.colors.line, MaterialTheme.shapes.large)
        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large).padding(Tokens.Space4),
    verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
) { content() }

/** The round 72 dp button of design/beta.md 2.3 (volume-2, white). */
@Composable
private fun PlayButton(onClick: () -> Unit) = Box(
    Modifier.size(72.dp).background(JustStudyTheme.colors.action, CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "Hear it" },
    contentAlignment = Alignment.Center,
) { LucideIcon(Lucide.Volume2, contentDescription = null, size = 32.dp, tint = Color.White) }

/** Listening Lab (design/beta.md 4): mode, speed, and Dictation, Shadowing or Hands-free. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningContent(
    ui: ListeningUi,
    speaking: SpeakingUi,
    systemKeyboard: Boolean,
    voiceReady: Boolean,
    permissionNeeded: Boolean,
    onBack: () -> Unit = {},
    onMode: (ListenMode) -> Unit = {},
    onRate: (Float) -> Unit = {},
    onPlay: () -> Unit = {},
    onReplay: () -> Unit = {},
    onSlower: () -> Unit = {},
    onType: (String) -> Unit = {},
    onCheck: () -> Unit = {},
    onNext: () -> Unit = {},
    onPause: () -> Unit = {},
    onSystemKeyboard: (Boolean) -> Unit = {},
    speakActions: SpeakActions = SpeakActions(),
) {
    val colors = JustStudyTheme.colors
    val ink = MaterialTheme.colorScheme.onSurface
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val item = ui.current
    val typing = ui.mode == ListenMode.Dictation && ui.result == null && item != null && ui.reason == null
    val ime = remember(ui.index, ui.mode) { HangulIme() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding().nestedScroll(scroll.nestedScrollConnection)) {
        JsTopBar("Listening Lab", "듣기 연습", "Train your ear with your own sentences.", null, scroll, onMore = null, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space4), verticalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
            SelectedGroup(listOf("Dictation", "Shadowing", "Hands-free"), ListenMode.entries.indexOf(ui.mode), { onMode(ListenMode.entries[it]) }, Modifier.fillMaxWidth())
            if (ui.mode != ListenMode.Shadowing) {
                SelectedGroup(listOf("0.7×", "1×", "1.2×"), RATES.indexOf(ui.rate).coerceAtLeast(0), { onRate(RATES[it]) }, Modifier.fillMaxWidth())
            }
            when {
                ui.loading -> LoadingState()
                ui.reason != null || item == null -> EmptyState(Lucide.Headphones, "Nothing to listen to yet", null, ui.reason ?: "Add sentences with a translation to your notes.")
                ui.mode == ListenMode.Dictation -> {
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
                            PlayButton(onPlay)
                            StickerButton("Replay", onReplay, color = StickerColor.Gray, size = StickerSize.Small, icon = Lucide.RotateCcw)
                            StickerButton("Slower", onSlower, color = StickerColor.Gray, size = StickerSize.Small, icon = Lucide.Turtle)
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                Box(Modifier.size(48.dp).clickable(role = Role.Switch) { onSystemKeyboard(!systemKeyboard) }.semantics { contentDescription = if (systemKeyboard) "Use the Hangul keys" else "Use the phone's keyboard" }, contentAlignment = Alignment.Center) {
                                    LucideIcon(Lucide.Keyboard, contentDescription = null, tint = if (systemKeyboard) colors.brand else colors.control)
                                }
                            }
                        }
                        if (ui.result == null) {
                            if (systemKeyboard) JsTextField(ui.typed, onType, placeholder = "Type what you hear", center = true, onDone = onCheck)
                            else Text(ui.typed.ifEmpty { " " }, Modifier.fillMaxWidth().softBox().padding(Tokens.Space3), style = JustStudyTheme.korean.koPrompt, color = ink)
                        }
                    }
                    ui.result?.let { r ->
                        ResultBanner(if (r.correct) BannerTone.Correct else BannerTone.Wrong, if (r.correct) "Correct!" else "Not quite", ko = if (r.correct) "정답" else "오답")
                        TypedResult(r.typed, r.comparison)
                        Text(item.meaning, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    }
                }
                ui.mode == ListenMode.Shadowing -> {
                    Card {
                        Text(item.korean, style = JustStudyTheme.korean.koTitle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = ink)
                        Text(item.meaning, fontSize = 14.sp, color = colors.muted)
                        SpeakingBlock(speaking, speakActions, permissionNeeded)
                    }
                    speaking.comparison?.let { c ->
                        if (c.matches) ResultBanner(BannerTone.Correct, "Matches your notes.")
                        Comparison(c, null, voiceReady, speakActions.onListen)
                    }
                }
                else -> {
                    Text("${ui.dueCount} cards due · not rated here", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    Card {
                        Text(item.korean, style = JustStudyTheme.korean.koTitle.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold), color = ink)
                        Text(if (ui.showMeaning) item.meaning else " ", fontSize = 18.sp, color = colors.muted)
                    }
                }
            }
        }
        if (!ui.loading && ui.reason == null && item != null) {
            if (typing && !systemKeyboard) {
                HangulKeyboard(
                    onJamo = { ime.input(it); onType(ime.text) }, onSpace = { ime.space(); onType(ime.text) }, onDelete = { ime.delete(); onType(ime.text) },
                    onCheck = onCheck, checkEnabled = ui.typed.isNotBlank(), enabled = true,
                )
            }
            Column(Modifier.fillMaxWidth().padding(Tokens.Space3), verticalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
                when {
                    ui.mode == ListenMode.HandsFree -> {
                        StickerButton(if (ui.running) "Pause" else "Resume", if (ui.running) onPause else onPlay, Modifier.fillMaxWidth(), color = StickerColor.Action, size = StickerSize.Large)
                        StickerButton("Next", onNext, Modifier.fillMaxWidth(), ko = "다음", color = StickerColor.Gray, size = StickerSize.Large)
                    }
                    typing && systemKeyboard -> StickerButton("Check", onCheck, Modifier.fillMaxWidth(), ko = "확인", color = StickerColor.Go, size = StickerSize.Large, enabled = ui.typed.isNotBlank())
                    !typing -> StickerButton("Next", onNext, Modifier.fillMaxWidth(), ko = "다음", color = StickerColor.Go, size = StickerSize.Large)
                }
            }
        }
    }
}
