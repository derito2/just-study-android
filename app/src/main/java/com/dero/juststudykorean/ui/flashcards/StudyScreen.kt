package com.dero.juststudykorean.ui.flashcards

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
    BackHandler(onBack = onBack) // every rating is already saved
    StudyContent(
        ui, deckName, direction, typeAnswer, scheduling, onBack = onBack, onDirection = { vm.setDirection(it) },
        onTypeAnswer = { vm.setTypeAnswer(it) }, onType = vm::type, onFlip = vm::flip, onRate = vm::rate,
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
    onFlip: () -> Unit,
    onRate: (Rating) -> Unit,
) {
    val colors = JustStudyTheme.colors
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = Tokens.Space1, end = Tokens.Space4), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { LucideIcon(Lucide.ChevronLeft, contentDescription = "Back to decks", tint = ink) }
            Text(deckName, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!ui.loading) Text("${ui.queue.size} left", style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Tokens.Space3).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
            directions.forEach { (d, label) -> FilterPill(label, d == direction, { onDirection(d) }) }
        }
        Row(
            Modifier.fillMaxWidth().height(48.dp).toggleable(typeAnswer, role = Role.Switch, onValueChange = onTypeAnswer).padding(horizontal = Tokens.Space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text("Type the answer", Modifier.alignByBaseline(), style = JustStudyTheme.korean.bodyBold, color = ink)
                KoSide("답 입력하기", 14.sp, Modifier.alignByBaseline(), color = colors.muted)
            }
            ThemeSwitch(checked = typeAnswer, onCheckedChange = null)
        }
        val card = ui.card
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Tokens.Space3)) {
            when {
                ui.loading -> LoadingState("Loading cards…")
                ui.error != null && card == null -> ErrorState(ui.error, onBack)
                card == null -> EmptyState(
                    Lucide.Layers, "No cards due in this deck. Don't get comfortable.", null, null,
                    action = "Back to decks", actionKo = "덱으로 돌아가기", onAction = onBack,
                )
                else -> {
                    val side = sideFor(direction, card)
                    val enToKo = side == CardDirection.EnToKo
                    val typing = typingFor(typeAnswer, card, ui.details)
                    // The next card comes up 8 dp (240 ms).
                    val rise = remember(card.card.id) { Animatable(if (ui.reviewed > 0) 1f else 0f) }
                    val reduce = reduceMotion()
                    LaunchedEffect(card.card.id) { if (reduce) rise.snapTo(0f) else rise.animateTo(0f, tween(Motion.Base, easing = Motion.EaseOut)) }
                    val density = LocalDensity.current.density
                    val source = ui.sources[card.card.id]?.let { "Source: ${it.filename}, page ${it.page}" }
                    Column(
                        Modifier.fillMaxSize().graphicsLayer { translationY = 8 * density * rise.value }.then(if (ui.flipped) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                        verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
                    ) {
                        FlipCard(
                            ui.flipped, modifier = if (ui.flipped) Modifier else Modifier.weight(1f),
                            front = { m ->
                                CardFront(
                                    if (enToKo) card.card.back else card.card.front, korean = !enToKo, isNew = card.isNew,
                                    modifier = m,
                                    typingField = if (typing) ({
                                        JsTextField(ui.typed, onType, placeholder = if (enToKo) "Type it in Korean" else "Type the meaning", center = true, onDone = onFlip)
                                    }) else null,
                                )
                            },
                            back = { m ->
                                CardBack(
                                    card.card.front, card.card.romanization,
                                    card.card.back, source, m,
                                    meaningFirst = typing && enToKo,
                                    typed = if (typing) ({ TypedResult(ui.typed, compareAnswer(ui.typed, if (enToKo) card.card.front else card.card.back, korean = enToKo)) }) else null,
                                )
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
                    StickerButton("Show answer", onFlip, Modifier.fillMaxWidth(), ko = "정답 보기")
                } else {
                    RatingButtons(preview(card2, scheduling), onRate)
                }
            }
        }
    }
}
