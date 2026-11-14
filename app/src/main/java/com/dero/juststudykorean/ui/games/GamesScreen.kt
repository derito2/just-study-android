package com.dero.juststudykorean.ui.games

import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.data.games.SessionState
import com.dero.juststudykorean.data.games.SessionSummary
import com.dero.juststudykorean.korean.games.GameItem
import com.dero.juststudykorean.korean.games.GameType
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.components.EmptyState
import com.dero.juststudykorean.ui.components.ErrorState
import com.dero.juststudykorean.ui.components.KoSide
import com.dero.juststudykorean.ui.components.LoadingState
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.components.StickerSize
import com.dero.juststudykorean.ui.components.ThemeSwitch
import com.dero.juststudykorean.ui.games.pieces.LocalKoreanVoice
import com.dero.juststudykorean.ui.games.pieces.GameName
import com.dero.juststudykorean.ui.games.pieces.Instruction
import com.dero.juststudykorean.ui.games.pieces.OriginMark
import com.dero.juststudykorean.ui.games.stage.BOARD_TOP_DP
import com.dero.juststudykorean.ui.games.stage.GameStage
import com.dero.juststudykorean.ui.games.stage.STAGE_DP
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.Poppins
import com.dero.juststudykorean.ui.theme.Tokens
import com.dero.juststudykorean.ui.theme.reduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** TalkBack is on (no automatic advance, no clocks: games.md 12). Null: ask the system; tests set it. */
val LocalTalkBack = staticCompositionLocalOf<Boolean?> { null }

@Composable
fun talkBackOn(): Boolean {
    LocalTalkBack.current?.let { return it }
    val context = LocalContext.current
    return remember(context) { (context.getSystemService(AccessibilityManager::class.java))?.isTouchExplorationEnabled == true }
}

/** Games (design/games.md): full screen, no catalogue; entering plays. */
@Composable
fun GamesScreen(onBack: () -> Unit, onLibrary: () -> Unit, vm: GamesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val typingTimer by vm.typingTimer.collectAsStateWithLifecycle()
    val bank by vm.bank.collectAsStateWithLifecycle()
    val voiceMissing by vm.voiceMissing.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.start() }
    // 9: it pauses by itself when the screen loses the focus (a call, another app, the screen off).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) vm.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler { if (state is SessionState.Ended || state is SessionState.Empty || state is SessionState.Failed) onBack() else vm.pause() }
    CompositionLocalProvider(LocalGameBank provides bank, LocalKoreanVoice provides vm.voice) {
        GamesContent(
            state, counts, typingTimer,
            onResult = vm::submit, onFeedbackDone = { vm.feedbackDone() }, onPause = vm::pause, onResume = vm::resume,
            onEnd = { vm.end() }, onPlayAgain = vm::playAgain, onDone = onBack, onLibrary = onLibrary, onTypingTimer = { vm.setTypingTimer(it) }, voiceMissing = voiceMissing,
        )
    }
}

/** The screen without its ViewModel (screenshot and behaviour tests). [openingAtStart]: false skips the opening. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesContent(
    state: SessionState,
    counts: SessionCounts,
    typingTimer: Boolean,
    onResult: (RoundResult) -> Unit,
    onFeedbackDone: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onPlayAgain: () -> Unit,
    onDone: () -> Unit,
    onLibrary: () -> Unit,
    onTypingTimer: (Boolean) -> Unit,
    openingAtStart: Boolean = true,
    pauseSheetInline: Boolean = false,
    voiceMissing: Boolean = false,
) {
    val still = LocalFrozenMotion.current || reduceMotion()
    val stage = remember { StageScope() }
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 8: the opening, once per screen (not after a pause or a rotation); a tap jumps to its step 2.
    var opened by rememberSaveable { mutableStateOf(!openingAtStart || still) }
    val full = remember { Animatable(if (opened) 0f else 1f) } // 1: the scene fills the screen
    val ended = state is SessionState.Ended
    LaunchedEffect(opened) {
        if (!opened) { delay(700); opened = true }
    }
    LaunchedEffect(opened, ended) {
        val target = if (opened && !ended) 0f else 1f
        if (still) full.snapTo(target) else full.animateTo(target, tween(Motion.Slow, easing = Motion.EaseInOut))
    }
    val round = when (state) {
        is SessionState.Playing -> state.round
        is SessionState.Paused -> state.round
        is SessionState.Feedback -> state.round
        else -> null
    }
    val game = round?.game
    LaunchedEffect(game) { if (round == null) stage.clear() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Tokens.Night)) {
        val maxH = maxHeight
        val stageH = top + STAGE_DP.dp
        GameStage(
            stage, parallaxKey = game, burst = counts.burst,
            modifier = Modifier.fillMaxWidth().height(maxH),
            verticalScale = { 1f + full.value * ((maxH - top).value / STAGE_DP - 1f) },
        )
        // The opening's title, and the summary's lanterns, on the full scene.
        if (!opened || full.value > 0.01f && !ended) OpeningTitle({ full.value }, Modifier.align(Alignment.Center).clickable(remember { MutableInteractionSource() }, null) { opened = true })
        if (ended && !still) RisingLanterns(((state as SessionState.Ended).summary.won / 3).toInt().coerceAtMost(12), Modifier.fillMaxSize())
        if (!ended) {
            GameTopBar(game, counts, onPause, Modifier.padding(top = top).graphicsLayer { alpha = 1f - full.value })
            // The board: from y = 144 dp under the status bar, rising from below during the opening.
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = top + BOARD_TOP_DP.dp)
                    .graphicsLayer { translationY = full.value * with(density) { (maxH - top - BOARD_TOP_DP.dp).toPx() } }
                    .board()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottom),
            ) {
                Board(state, round, stage, typingTimer, onResult, onLibrary, onDone)
            }
            if (state is SessionState.Feedback) {
                FeedbackRibbon(state.round, state.result, counts, onFeedbackDone, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 16.dp + bottom))
            }
            if (state is SessionState.Paused) {
                Box(Modifier.fillMaxSize().background(JustStudyTheme.colors.scrim).clickable(remember { MutableInteractionSource() }, null) { })
                if (pauseSheetInline) {
                    PauseSheetContent(counts, typingTimer, onTypingTimer, onResume, onEnd, voiceMissing, Modifier.align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)))
                } else {
                    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ModalBottomSheet(onDismissRequest = onResume, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
                        PauseSheetContent(counts, typingTimer, onTypingTimer, onResume, onEnd, voiceMissing)
                    }
                }
            }
        } else {
            SessionSummaryCard((state as SessionState.Ended).summary, counts, onPlayAgain, onDone, Modifier.align(Alignment.Center).padding(16.dp))
        }
    }
}

@Composable
private fun Modifier.board(): Modifier {
    val c = JustStudyTheme.colors
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    return this.background(MaterialTheme.colorScheme.surface, shape).border(2.dp, c.line, shape)
}

/** 5: pause, the game's name (entering from below when it changes), the won rounds and the streak (≥ 2). */
@Composable
private fun GameTopBar(game: GameType?, counts: SessionCounts, onPause: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(56.dp).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onPause, Modifier.size(48.dp)) { LucideIcon(Lucide.Pause, contentDescription = "Pause game", tint = Color.White) }
        Box(Modifier.weight(1f)) {
            val text = game?.let { GAME_TEXTS.getValue(it) }
            if (text != null) GameName(text.name, text.ko)
        }
        Pill(Lucide.Check, counts.won.toString(), "${counts.won} rounds won", Color.White)
        if (counts.streak >= 2) {
            Spacer(Modifier.width(8.dp))
            Pill(Lucide.Flame, counts.streak.toString(), "Streak ${counts.streak}", Tokens.Lantern)
        }
    }
}

@Composable
private fun Pill(icon: Lucide, text: String, description: String, tint: Color) {
    Row(
        Modifier.height(28.dp).background(Color.White.copy(alpha = 0.12f), CircleShape).padding(horizontal = 10.dp).clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(icon, contentDescription = null, size = 14.dp, tint = tint)
        Spacer(Modifier.width(4.dp))
        Text(text, style = JustStudyTheme.korean.chip, color = Color.White)
    }
}

/** The board's content by state (11), and the round with its instruction, hint and origin (6). */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Board(state: SessionState, round: RoundSpec?, stage: StageScope, typingTimer: Boolean, onResult: (RoundResult) -> Unit, onLibrary: () -> Unit, onBack: () -> Unit) {
    when {
        state is SessionState.Empty -> EmptyState(
            Lucide.Gamepad2, "No notes to play with yet", "아직 노트가 없어요", "Add a PDF in Library and your words become games.",
            action = "Go to Library", onAction = onLibrary,
        )
        state is SessionState.Failed -> ErrorState("Games couldn't start this time.", onBack, retryLabel = "Back")
        round == null -> LoadingState()
        else -> {
            val paused = state is SessionState.Paused
            val answered = state is SessionState.Feedback
            val text = GAME_TEXTS.getValue(round.game)
            val (instruction, ko) = instructionOf(round, text)
            Instruction(instruction, ko, hintOf(round))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                // A new round starts from scratch; its state lives as long as the round.
                androidx.compose.runtime.key(round) {
                    val clock = rememberRoundClock(paused || answered)
                    GameRound(round, paused || answered, stage, typingTimer && !talkBackOn()) { r -> onResult(r.copy(responseMs = clock())) }
                }
            }
            OriginMark(originOf(round, LocalGameBank.current), Modifier.padding(top = 8.dp))
        }
    }
}

/** Milliseconds the round has been played, without its pauses. */
@Composable
private fun rememberRoundClock(paused: Boolean): () -> Long {
    val started = remember { System.nanoTime() }
    var pausedTotal by remember { mutableLongStateOf(0L) }
    var pausedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(paused) { if (paused) pausedAt = System.nanoTime() else if (pausedAt != 0L) { pausedTotal += System.nanoTime() - pausedAt; pausedAt = 0L } }
    return { (System.nanoTime() - started - pausedTotal) / 1_000_000 }
}

/** 7.2: the ribbon over the board's bottom. A tap anywhere moves on (not before 350 ms); with TalkBack, Next. */
@Composable
private fun FeedbackRibbon(round: RoundSpec, result: RoundResult, counts: SessionCounts, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = JustStudyTheme.colors
    val talkBack = talkBackOn()
    val shownAt = remember(result) { System.nanoTime() }
    val ms = if (result.outcome == Outcome.WON) 900L else 1800L
    LaunchedEffect(result) { if (!talkBack) { delay(ms); onDone() } }
    val tap = { if (System.nanoTime() - shownAt >= 350_000_000L) onDone() }
    val (fill, border, icon) = when {
        result.timedOut -> Triple(c.wrongFill, c.wrongBorder, Lucide.Clock)
        result.outcome == Outcome.WON -> Triple(c.correctFill, c.correctDeco, Lucide.Check)
        result.outcome == Outcome.NEUTRAL -> Triple(c.tint, c.tintBorder, Lucide.Lightbulb)
        else -> Triple(c.wrongFill, c.wrongBorder, Lucide.X)
    }
    val ink = when { result.outcome == Outcome.WON -> c.correct; result.outcome == Outcome.NEUTRAL -> c.brandText; else -> c.wrong }
    val (title, ko) = when {
        result.timedOut -> "Time's up" to "시간 종료"
        result.outcome == Outcome.WON -> when (counts.streak) { 3 -> "Three in a row!"; 5 -> "On fire!"; 10 -> "Unstoppable!"; else -> "Correct!" } to "정답!"
        result.outcome == Outcome.NEUTRAL -> neutralText(round) to null
        counts.lostInARow > 2 -> "Almost. Keep going." to "아쉬워요"
        else -> "Not quite" to "아쉬워요"
    }
    val answer = if (result.outcome == Outcome.WON) null else answerOf(round)
    // Neutral (Sentence Builder) shows the order of your notes under its text.
    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClick = tap))
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).background(fill, RoundedCornerShape(16.dp)).border(2.dp, border, RoundedCornerShape(16.dp))
            .clickable(remember { MutableInteractionSource() }, null, onClick = tap).padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(icon, contentDescription = null, size = 20.dp, tint = ink)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, Modifier.alignByBaseline(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ink)
                if (ko != null) KoSide(ko, 16.sp, Modifier.alignByBaseline(), color = ink)
            }
            if (answer != null) Row(verticalAlignment = Alignment.Bottom) {
                Text("Answer: ", fontSize = 14.sp, color = c.fillMuted)
                Text(answer, style = JustStudyTheme.korean.koBody, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (talkBack) StickerButton("Next", onDone, color = StickerColor.Action, size = StickerSize.Small)
    }
}

/** 9: the pause sheet's content: four figures, the typing timer, Resume and End session. */
@Composable
fun PauseSheetContent(counts: SessionCounts, typingTimer: Boolean, onTypingTimer: (Boolean) -> Unit, onResume: () -> Unit, onEnd: () -> Unit, voiceMissing: Boolean = false, modifier: Modifier = Modifier) {
    // Scrolls when it doesn't fit (a small screen, 130 % text, the no-voice notice).
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Paused", Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            KoSide("일시정지", MaterialTheme.typography.titleMedium.fontSize, Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.onSurface)
        }
        Figures(counts.rounds.toLong(), counts.won.toLong(), counts.bestStreak.toLong(), counts.seconds)
        Row(Modifier.fillMaxWidth().clickable { onTypingTimer(!typingTimer) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Timer in typing games", style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
                Text("타자 시간 제한", style = JustStudyTheme.korean.koBelow, color = JustStudyTheme.colors.muted)
            }
            ThemeSwitch(typingTimer, onTypingTimer)
        }
        // games.md 9: no button; the app never opens system settings.
        if (voiceMissing) Row(
            Modifier.fillMaxWidth().background(JustStudyTheme.colors.canvas, RoundedCornerShape(12.dp)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            LucideIcon(Lucide.Volume2, contentDescription = null, size = 20.dp, tint = JustStudyTheme.colors.muted)
            Text("Listening games are off: no offline Korean voice on this phone. Install one in Android's text-to-speech settings.", fontSize = 12.sp, color = JustStudyTheme.colors.muted)
        }
        StickerButton("Resume", onResume, Modifier.fillMaxWidth(), ko = "계속하기", color = StickerColor.Go)
        StickerButton("End session", onEnd, Modifier.fillMaxWidth().padding(top = 0.dp), ko = "끝내기", color = StickerColor.Gray)
    }
}

/** Rounds · Won · Best streak · Time, in a row. */
@Composable
private fun Figures(rounds: Long, won: Long, best: Long, seconds: Long) {
    val c = JustStudyTheme.colors
    Row(Modifier.fillMaxWidth()) {
        listOf(
            Triple("ROUNDS", "라운드", rounds.toString()), Triple("WON", "정답", won.toString()),
            Triple("BEST STREAK", "최고 연속", best.toString()), Triple("TIME", "시간", "%d:%02d".format(seconds / 60, seconds % 60)),
        ).forEachIndexed { i, (label, ko, value) ->
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = c.muted, textAlign = TextAlign.Center)
                Text(ko, style = JustStudyTheme.korean.koBelow, color = c.muted)
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = if (i == 1) c.correct else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** 10: the summary card over the full scene. */
@Composable
fun SessionSummaryCard(summary: SessionSummary, counts: SessionCounts, onPlayAgain: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = JustStudyTheme.colors
    Column(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val (title, ko) = if (summary.rounds == 0L) "See you next time" to "다음에 봐요" else "Nice session" to "수고했어요"
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, Modifier.alignByBaseline(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            KoSide(ko, 24.sp, Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.onSurface)
        }
        Figures(summary.rounds, summary.won, summary.bestStreak, summary.durationSeconds)
        HorizontalDivider(thickness = Tokens.Border, color = c.line)
        Text("WORDS TO REVIEW · 복습할 단어", style = MaterialTheme.typography.labelSmall, color = c.muted)
        val review = counts.failed.takeLast(5)
        if (review.isEmpty()) Text("Nothing to review. Clean session.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        review.forEach { item: GameItem ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.korean, style = JustStudyTheme.korean.koBody.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Text(item.meaning, style = MaterialTheme.typography.bodyMedium, color = c.muted, maxLines = 1)
            }
        }
        StickerButton("Play again", onPlayAgain, Modifier.fillMaxWidth(), ko = "다시 하기", color = StickerColor.Go)
        StickerButton("Done", onDone, Modifier.fillMaxWidth(), ko = "완료", color = StickerColor.Action)
    }
}

/** 8: Games · 게임 on the full scene, entering as the splash's title. */
@Composable
private fun OpeningTitle(full: () -> Float, modifier: Modifier = Modifier) {
    Column(modifier.graphicsLayer { alpha = full(); val s = 0.8f + 0.2f * full(); scaleX = s; scaleY = s }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Games", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
        Text("게임", color = Tokens.SkyLight, fontSize = 18.sp)
    }
}

/** 10: one lantern per 3 rounds won (max 12) rising across the screen; none with "Remove animations". */
@Composable
private fun RisingLanterns(count: Int, modifier: Modifier = Modifier) {
    if (count == 0) return
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(3600 + 180 * count, easing = LinearEasing)) }
    val seeds = remember(count) { val r = Random(count); List(count) { Triple(r.nextFloat(), 2600f + r.nextFloat() * 1000f, r.nextFloat() * 6f) } }
    Box(
        modifier.clearAndSetSemantics { }.drawWithCache {
            onDrawBehind {
                val total = 3600f + 180f * count
                seeds.forEachIndexed { i, (x, dur, phase) ->
                    val local = ((t.value * total - i * 180f) / dur).coerceIn(0f, 1f)
                    if (local <= 0f || local >= 1f) return@forEachIndexed
                    val u = 1.dp.toPx()
                    val cx = x * size.width + 12f * u * kotlin.math.sin(local * 6f + phase)
                    val cy = size.height * (1.05f - 1.1f * local)
                    val s = 1f - 0.3f * local
                    drawCircle(Tokens.Lantern.copy(alpha = 0.3f), 20f * u * s, Offset(cx, cy))
                    drawRoundRect(Tokens.LanternRed, Offset(cx - 9f * u * s, cy - 12f * u * s), Size(18f * u * s, 24f * u * s), CornerRadius(6f * u * s))
                    drawRoundRect(Tokens.Lantern, Offset(cx - 4f * u * s, cy - 7f * u * s), Size(8f * u * s, 14f * u * s), CornerRadius(3f * u * s))
                }
            }
        },
    )
}
