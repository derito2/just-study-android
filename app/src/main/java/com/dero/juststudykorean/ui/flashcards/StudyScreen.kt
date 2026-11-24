package com.dero.juststudykorean.ui.flashcards

import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import com.dero.juststudykorean.ui.components.FileName
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.data.prefs.CardDirection
import com.dero.juststudykorean.korean.Rating
import com.dero.juststudykorean.korean.Scheduling
import com.dero.juststudykorean.korean.compareAnswer
import com.dero.juststudykorean.korean.flashcards.CardMode
import com.dero.juststudykorean.ui.components.Pill
import com.dero.juststudykorean.ui.components.PillTone
import androidx.compose.foundation.layout.fillMaxHeight
import com.dero.juststudykorean.ui.components.CardBack
import com.dero.juststudykorean.ui.components.CardFront
import com.dero.juststudykorean.ui.components.EmptyState
import com.dero.juststudykorean.ui.components.ErrorState
import com.dero.juststudykorean.ui.components.FilterPill
import com.dero.juststudykorean.ui.components.FlipCard
import com.dero.juststudykorean.ui.components.FromYourNotes
import com.dero.juststudykorean.ui.components.JsTextField
import com.dero.juststudykorean.ui.components.KoSide
import com.dero.juststudykorean.ui.components.LoadingState
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.RatingButtons
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerSize
import com.dero.juststudykorean.ui.components.ThemeSwitch
import com.dero.juststudykorean.ui.components.TypedResult
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.Tokens
import com.dero.juststudykorean.ui.theme.reduceMotion

@Composable
fun StudyScreen(deckId: Long?, deckName: String, onBack: () -> Unit, vm: StudyViewModel = hiltViewModel()) {
    LaunchedEffect(deckId) { vm.open(deckId) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val direction by vm.direction.collectAsStateWithLifecycle()
    val typeAnswer by vm.typeAnswer.collectAsStateWithLifecycle()
    val scheduling by vm.scheduling.collectAsStateWithLifecycle()
    val gap by vm.gapCards.collectAsStateWithLifecycle()
    val listen by vm.listenCards.collectAsStateWithLifecycle()
    val voiceReady by vm.voiceReady.collectAsStateWithLifecycle()
    val voiceMissing by vm.voiceMissing.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack) // every rating is already saved
    StudyContent(
        ui, deckName, direction, typeAnswer, scheduling, onBack = onBack, onDirection = { vm.setDirection(it) },
        onTypeAnswer = { vm.setTypeAnswer(it) }, onType = vm::type, onFlip = vm::flip, onRate = vm::rate,
        gap = gap, listen = listen, voiceReady = voiceReady, voiceMissing = voiceMissing,
        onGap = { vm.setGapCards(it) }, onListen = { vm.setListenCards(it) }, onSpeak = vm::speak,
    )
}

private val directions = listOf(CardDirection.KoToEn to "Korean → English", CardDirection.EnToKo to "English → Korean", CardDirection.Both to "Both")

/** Review session (design/flashcards.md C): small top bar, options, the card, and Show answer or the ratings. */
@Composable
fun StudyContent(
    ui: StudyUi,
    deckName: String,
    direction: CardDirection,
    typeAnswer: Boolean,
    scheduling: Scheduling,
    onBack: () -> Unit,
    onDirection: (CardDirection) -> Unit,
    onTypeAnswer: (Boolean) -> Unit,
    onType: (String) -> Unit,
    onFlip: (meaning: Pair<String, String>?) -> Unit,
    onRate: (Rating) -> Unit,
    // F2 (design/beta.md 2): Fill the Gap, Listen Only and 🔊.
    gap: Boolean = false,
    listen: Boolean = false,
    voiceReady: Boolean = false,
    voiceMissing: Boolean = false,
    onGap: (Boolean) -> Unit = {},
    onListen: (Boolean) -> Unit = {},
    onSpeak: (String) -> Unit = {},
) {
    val colors = JustStudyTheme.colors
    val ink = MaterialTheme.colorScheme.onSurface
    // M-4: a rated card leaves 40 dp to the side (left with Again, right otherwise) and fades (150 ms) before the next one comes up.
    val exit = remember { Animatable(0f) }
    var exitSide by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val reduceMotion = reduceMotion()
    LaunchedEffect(ui.card?.card?.id) { exit.snapTo(0f) }
    val rate: (Rating) -> Unit = { rating ->
        exitSide = if (rating == Rating.Again) -1f else 1f
        scope.launch {
            if (!reduceMotion) exit.animateTo(1f, tween(150, easing = Motion.EaseOut))
            onRate(rating)
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = Tokens.Space1, end = Tokens.Space4), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { LucideIcon(Lucide.ChevronLeft, contentDescription = "Back to decks", tint = ink) }
            FileName(deckName, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1)
            if (!ui.loading) Text("${ui.queue.size} left", style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Tokens.Space3).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
            directions.forEach { (d, label) -> FilterPill(label, d == direction, { onDirection(d) }) }
        }
        // P-27: typing only works on word cards. With none in the deck the switch is locked (unless it is already on, so it can be
        // turned off), and the reason goes under its label, not inside the card.
        val canType = ui.queue.any { typingFor(true, it, ui.details) }
        val switchOn = !ui.loading && ui.queue.isNotEmpty()
        val locked = switchOn && !canType && !typeAnswer
        Row(
            Modifier.fillMaxWidth().height(48.dp).alpha(if (locked) 0.5f else 1f)
                .toggleable(typeAnswer, enabled = !locked, role = Role.Switch, onValueChange = onTypeAnswer).padding(horizontal = Tokens.Space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text("Type the answer", Modifier.alignByBaseline(), style = JustStudyTheme.korean.bodyBold, color = ink)
                KoSide("답 입력하기", 14.sp, Modifier.alignByBaseline(), color = colors.muted)
            }
            ThemeSwitch(checked = typeAnswer, onCheckedChange = null)
        }
        val card = ui.card
        if (switchOn && (!canType || (typeAnswer && card != null && !typingFor(true, card, ui.details)))) {
            Text("Typing works on word cards.", Modifier.padding(start = Tokens.Space3, end = Tokens.Space3, bottom = Tokens.Space2), fontSize = 12.sp, color = colors.muted)
        }
        // 2.1: one 48 dp row in two halves; Listen Only needs the voice (it can still be turned off without it).
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = Tokens.Space3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
            ModeSwitch("Fill the Gap", "빈칸", gap, enabled = true, onGap, Modifier.weight(1f))
            ModeSwitch("Listen Only", "듣기", listen, enabled = voiceReady || listen, onListen, Modifier.weight(1f))
        }
        if (voiceMissing) Text("Needs the phone's Korean voice.", Modifier.padding(start = Tokens.Space3, end = Tokens.Space3, bottom = Tokens.Space2), style = MaterialTheme.typography.bodySmall, color = colors.muted)
        if (ui.volumeHint) Pill("Turn the volume up to hear it.", PillTone.Warn, Modifier.padding(start = Tokens.Space3, bottom = Tokens.Space2))
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Tokens.Space3)) {
            when {
                ui.loading -> LoadingState("Loading cards…")
                ui.error != null && card == null -> ErrorState(ui.error, onBack)
                card == null -> EmptyState(
                    Lucide.Layers, "No cards due in this deck. Don't get comfortable.", null, null,
                    action = "Back to decks", actionKo = "덱으로 돌아가기", onAction = onBack,
                )
                else -> {
                    val mode = cardMode(card, ui, gap, listen, voiceReady)
                    val cloze = ui.clozes?.get(card.card.id)?.takeIf { mode == CardMode.Cloze }
                    val listening = mode == CardMode.Listen
                    // Fill the Gap and Listen Only show the Korean on the back, whatever the direction.
                    val enToKo = mode == CardMode.Classic && sideFor(direction, card) == CardDirection.EnToKo
                    val typing = typingFor(typeAnswer, card, ui.details)
                    val typedKorean = enToKo || mode != CardMode.Classic // what you type is Korean
                    val typedAnswer = cloze?.answer ?: if (typedKorean) card.card.front else card.card.back
                    val meaning = if (typing && mode == CardMode.Classic && !enToKo) card.card.front to card.card.back else null // what the meaning judge may check
                    val word = card.card.koreanWord
                    val speakWord: (() -> Unit)? = if (voiceReady) ({ onSpeak(word) }) else null
                    LaunchedEffect(card.card.id, listening) { if (listening && !ui.flipped) onSpeak(word) } // 2.3: Listen sounds by itself, once
                    // The next card comes up 8 dp (240 ms).
                    val rise = remember(card.card.id) { Animatable(if (ui.reviewed > 0) 1f else 0f) }
                    val reduce = reduceMotion()
                    LaunchedEffect(card.card.id) { if (reduce) rise.snapTo(0f) else rise.animateTo(0f, tween(Motion.Base, easing = Motion.EaseOut)) }
                    val density = LocalDensity.current.density
                    val source = ui.sources[card.card.id]?.let { "Source: ${it.filename}, page ${it.page}" }
                    Column(
                        Modifier.fillMaxSize().graphicsLayer { translationY = 8 * density * rise.value; translationX = exitSide * 40 * density * exit.value; alpha = 1f - exit.value }.then(if (ui.flipped) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                        verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
                    ) {
                        FlipCard(
                            ui.flipped, modifier = if (ui.flipped) Modifier else Modifier.weight(1f),
                            front = { m ->
                                val field: (@Composable () -> Unit)? = if (typing) ({
                                    val hint = when { cloze != null -> "Type the missing word"; listening -> "Type what you hear"; enToKo -> "Type it in Korean"; else -> "Type the meaning" }
                                    JsTextField(ui.typed, onType, placeholder = hint, center = true, onDone = { onFlip(meaning) })
                                }) else null // typing is on but this is a grammar card: the notice is under the switch (P-27)
                                when {
                                    cloze != null -> GapFront(cloze, m, field)
                                    listening -> ListenFront({ onSpeak(word) }, m, field)
                                    else -> WithSpeaker(speakWord.takeIf { !enToKo }, m) { inner ->
                                        CardFront(if (enToKo) card.card.back else card.card.front, korean = !enToKo, isNew = card.isNew, modifier = inner, typingField = field)
                                    }
                                }
                            },
                            back = { m ->
                                WithSpeaker(speakWord, m) { inner ->
                                    CardBack(
                                        card.card.front, card.card.romanization,
                                        card.card.back, source, inner,
                                        meaningFirst = typing && enToKo,
                                        typed = if (typing) ({ TypedResult(ui.typed, compareAnswer(ui.typed, typedAnswer, korean = typedKorean), aiAccepted = ui.aiAccepted == card.card.id) }) else null,
                                        extra = cloze?.let { c -> { GapSentence(c, if (voiceReady) ({ onSpeak(c.before + c.answer + c.after) }) else null) } },
                                    )
                                }
                            },
                        )
                        if (ui.flipped) {
                            val details = ui.details[card.card.id]
                            val withExample = details?.takeIf { it.examples.isNotEmpty() || card.card.exampleSentence.isNullOrEmpty() }
                                ?: (details ?: com.dero.juststudykorean.data.flashcards.CardDetails()).copy(
                                    examples = listOf(card.card.exampleSentence.orEmpty() to card.card.exampleTranslation.orEmpty()),
                                )
                            FromYourNotes(withExample)
                        }
                    }
                }
            }
        }
        val card2 = ui.card
        if (!ui.loading && card2 != null) {
            Box(Modifier.fillMaxWidth().padding(Tokens.Space3)) {
                if (!ui.flipped) {
                    val classic = cardMode(card2, ui, gap, listen, voiceReady) == CardMode.Classic
                    val meaning = if (classic && typingFor(typeAnswer, card2, ui.details) && sideFor(direction, card2) != CardDirection.EnToKo) card2.card.front to card2.card.back else null
                    StickerButton("Show answer", { onFlip(meaning) }, Modifier.fillMaxWidth(), ko = "정답 보기", size = StickerSize.Large)
                } else {
                    RatingButtons(preview(card2, scheduling), rate, enabled = exit.value == 0f)
                }
            }
        }
    }
}

/** 2.1: half of the modes row, like Type the answer: the name on the left, ThemeSwitch on the right, all of it tappable. */
@Composable
private fun ModeSwitch(name: String, ko: String, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxHeight().alpha(if (enabled) 1f else 0.5f).toggleable(on, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            Text(name, Modifier.alignByBaseline(), style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            KoSide(ko, 14.sp, Modifier.alignByBaseline(), color = JustStudyTheme.colors.muted)
        }
        ThemeSwitch(checked = on, onCheckedChange = null)
    }
}
