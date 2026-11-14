package com.dero.juststudykorean.ui.flashcards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.data.flashcards.Deck
import com.dero.juststudykorean.data.flashcards.ReviewItem
import com.dero.juststudykorean.korean.Scheduling
import com.dero.juststudykorean.ui.components.DeckCard
import com.dero.juststudykorean.ui.components.DocumentsField
import com.dero.juststudykorean.ui.components.DocumentsSheet
import com.dero.juststudykorean.ui.components.DropMark
import com.dero.juststudykorean.ui.components.EmptyState
import com.dero.juststudykorean.ui.components.ErrorState
import com.dero.juststudykorean.ui.components.JsDialog
import com.dero.juststudykorean.ui.components.JsSheet
import com.dero.juststudykorean.ui.components.JsSnackbarHost
import com.dero.juststudykorean.ui.components.JsTextField
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.KoSide
import com.dero.juststudykorean.ui.components.LoadingState
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.components.StickerSize
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens
import com.dero.juststudykorean.ui.theme.reduceMotion
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun DecksScreen(onMore: () -> Unit, onStudy: (Deck) -> Unit, vm: DecksViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val scheduling by vm.scheduling.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.events.collect { m ->
            launch {
                val r = snackbar.showSnackbar(m.text, actionLabel = if (m.undo != null) "Undo" else null, duration = if (m.undo != null) SnackbarDuration.Long else SnackbarDuration.Short) // R1-26
                if (r == SnackbarResult.ActionPerformed && m.undo != null) vm.undo(m.undo)
            }
        }
    }
    DecksContent(
        ui, scheduling, snackbar, onMore = onMore, onStudy = onStudy, onRetry = vm::retry, onDelete = { vm.delete(it) },
        onRename = { d, n -> vm.rename(d, n) }, onReorder = { vm.reorder(it) }, onMerge = vm::merge, onCreate = { vm.createCards(it) },
        onSaveScheduling = vm::saveScheduling,
    )
}

/** What the screen shows on top: the delete dialog, the combine dialog, a sheet. */
private sealed interface Over {
    data class Delete(val deck: Deck) : Over
    data class Merge(val target: Deck, val dragged: Deck) : Over
    data object Documents : Over
    data object Options : Over
}

/** Decks (design/flashcards.md A and B). [editingAtStart]/[mergeAtStart]: for screenshot tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecksContent(
    ui: DecksUi,
    scheduling: Scheduling,
    snackbar: SnackbarHostState,
    onMore: () -> Unit,
    onStudy: (Deck) -> Unit,
    onRetry: () -> Unit,
    onDelete: (Deck) -> Unit,
    onRename: (Deck, String) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onMerge: (List<Long>, String) -> Boolean,
    onCreate: (List<Long>) -> Unit,
    onSaveScheduling: (List<String>) -> String?,
    editingAtStart: Boolean = false,
    mergeAtStart: Pair<Int, Int>? = null,
    mergeNameAtStart: String = "",
) {
    val colors = JustStudyTheme.colors
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editing by rememberSaveable { mutableStateOf(editingAtStart) }
    var over by remember { mutableStateOf<Over?>(mergeAtStart?.let { (t, d) -> ui.decks?.let { Over.Merge(it[t], it[d]) } }) }
    var selectedDocs by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val reduce = reduceMotion()
    // Drag: the dragged deck, how far it moved, and where each deck is on screen.
    val bounds = remember { mutableStateMapOf<Long, Rect>() }
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val decks = ui.decks.orEmpty()
    fun target(): Pair<Deck, DropMark>? {
        val id = dragId ?: return null
        val center = (bounds[id] ?: return null).center + dragOffset
        val hit = decks.firstOrNull { it.id != id && bounds[it.id]?.contains(center) == true } ?: return null
        val r = bounds.getValue(hit.id)
        val x = (center.x - r.left) / r.width
        return hit to when { x < 0.2f -> DropMark.Before; x > 0.8f -> DropMark.After; else -> DropMark.On }
    }
    val hover = target()
    val fly = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
            JsTopBar(
                "Flashcards", "단어 카드", "Words from your own notes. Review them before you forget them, again.",
                "네 노트에서 나온 단어. 또 잊어버리기 전에 복습해.", scroll, onMore = onMore, onBack = null,
            )
            LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Tokens.Space4, end = Tokens.Space4, bottom = Tokens.Space6),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Space3), verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
                userScrollEnabled = dragId == null,
            ) {
                when {
                    ui.error -> full("error") { ErrorState("Couldn't load your decks.", onRetry) }
                    ui.decks == null -> full("loading") { LoadingState("Loading decks…") }
                    ui.decks.isEmpty() -> full("empty") {
                        EmptyState(
                            Lucide.Layers, "You don't have any cards yet", "아직 카드가 없어요",
                            "Create cards from a processed document and its deck will show up here.",
                            action = "Create cards", onAction = { onCreate(selectedDocs.toList()) },
                        )
                    }
                    else -> {
                        if (!editing) ui.stats?.let { s -> full("stats") { StatsRow(listOf("New" to s.new, "Due now" to s.dueNow, "Learned" to s.learned, "Total" to s.total)) } }
                        full("header") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f).semantics { heading() }, verticalAlignment = Alignment.Bottom) {
                                    Text("Continue flashcards", Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                                    KoSide("단어 카드 계속하기", MaterialTheme.typography.titleMedium.fontSize, Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.onBackground)
                                }
                                IconButton(onClick = { editing = !editing; dragId = null }) {
                                    LucideIcon(
                                        if (editing) Lucide.Check else Lucide.SquarePen,
                                        contentDescription = if (editing) "Done editing decks" else "Edit decks", tint = colors.control,
                                    )
                                }
                            }
                        }
                        if (editing) full("hint") {
                            Text(
                                "Tap a deck's name to rename it. Hold and drag a deck to move it, or drop it on another deck to combine them into one. Your cards and their progress don't change.",
                                style = MaterialTheme.typography.bodyMedium, color = colors.muted,
                            )
                        }
                        items(decks, key = { it.id }) { deck ->
                            val dragged = deck.id == dragId
                            DeckCard(
                                deck, onContinue = { onStudy(deck) }, onDelete = { over = Over.Delete(deck) },
                                modifier = Modifier
                                    .then(if (dragged) Modifier.zIndex(1f) else Modifier)
                                    .onGloballyPositioned { if (!dragged) bounds[deck.id] = it.boundsInRoot() }
                                    .graphicsLayer {
                                        if (dragged) { translationX = dragOffset.x; translationY = dragOffset.y }
                                        if (dragged && fly.value > 0f) { val s = 1f - 0.4f * fly.value; scaleX = s; scaleY = s; alpha = 1f - fly.value }
                                    }
                                    .then(
                                        if (editing) Modifier.pointerInput(deck.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { dragId = deck.id; dragOffset = Offset.Zero },
                                                onDrag = { change, amount -> change.consume(); dragOffset += amount },
                                                onDragCancel = { dragId = null },
                                                onDragEnd = {
                                                    val t = target()
                                                    when (t?.second) {
                                                        DropMark.On -> over = Over.Merge(t.first, deck)
                                                        DropMark.Before, DropMark.After -> {
                                                            val ids = decks.map { it.id }.toMutableList().apply { remove(deck.id) }
                                                            val at = ids.indexOf(t.first.id) + if (t.second == DropMark.After) 1 else 0
                                                            ids.add(at, deck.id)
                                                            onReorder(ids)
                                                            dragId = null
                                                        }
                                                        else -> dragId = null
                                                    }
                                                },
                                            )
                                        } else Modifier,
                                    ),
                                editing = editing, wiggle = editing && !reduce && !dragged, alternate = decks.indexOf(deck) % 2 == 1,
                                dragged = dragged, mark = if (hover?.first?.id == deck.id) hover.second else DropMark.None,
                                onRename = { onRename(deck, it) },
                            )
                        }
                    }
                }
                if (ui.decks != null && !ui.error) {
                    full("create") {
                        CreateCard(
                            selectedDocs, ui, onDocuments = { over = Over.Documents }, onCreate = { onCreate(selectedDocs.toList()) },
                            onOptions = { over = Over.Options },
                        )
                    }
                    full("history") { History(ui.history) }
                }
            }
        }
        JsSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    when (val o = over) {
        is Over.Delete -> JsDialog(
            null, null, "Delete \"${o.deck.name}\"? Its cards and their progress are deleted too.", { over = null },
            confirm = { m -> StickerButton("Yes, delete", { over = null; onDelete(o.deck) }, m, ko = "삭제", color = StickerColor.Danger) },
            dismiss = { m -> StickerButton("Cancel", { over = null }, m, ko = "취소", color = StickerColor.Gray) },
        )
        is Over.Merge -> MergeDialog(
            listOf(o.target.name, o.dragged.name),
            onCombine = { name ->
                over = null
                scope.launch {
                    if (!reduce) fly.animateTo(1f, tween(240))
                    onMerge(listOf(o.target.id, o.dragged.id), name)
                    fly.snapTo(0f); dragId = null
                }
            },
            onDismiss = { over = null; dragId = null },
            initial = mergeNameAtStart,
        )
        Over.Documents -> DocumentsSheet(ui.documents, selectedDocs, { selectedDocs = it }, { over = null })
        Over.Options -> OptionsSheet(scheduling, onSaveScheduling) { over = null }
        null -> Unit
    }
}

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun Modifier.card() = this
    .border(Tokens.Border, JustStudyTheme.colors.line, MaterialTheme.shapes.large)
    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)

/** New · Due now · Learned · Total: four Cards in a row, 8 dp apart. */
@Composable
private fun StatsRow(values: List<Pair<String, Int>>) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
        values.forEach { (label, n) ->
            Column(
                Modifier.weight(1f).fillMaxHeight().card().padding(horizontal = 4.dp, vertical = 10.dp).clearAndSetSemantics { contentDescription = "$label: $n" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = JustStudyTheme.colors.muted, maxLines = 1)
                Text("$n", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, ko: String) {
    Row(Modifier.semantics { heading() }, verticalAlignment = Alignment.Bottom) {
        Text(text, Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        KoSide(ko, MaterialTheme.typography.titleMedium.fontSize, Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Create flashcards: Documents, Create cards (Go) and Settings (Gray, Small). */
@Composable
private fun CreateCard(selected: Set<Long>, ui: DecksUi, onDocuments: () -> Unit, onCreate: () -> Unit, onOptions: () -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(Tokens.Space4), verticalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
        SectionTitle("Create flashcards", "단어 카드 만들기")
        Text("Documents", style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
        DocumentsField(selected, ui.documents, onDocuments)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
            StickerButton(if (ui.creating) "Creating…" else "Create cards", onCreate, Modifier.weight(1f), ko = "카드 만들기", color = StickerColor.Go, enabled = !ui.creating)
            StickerButton("Settings", onOptions, ko = "설정", color = StickerColor.Gray, size = StickerSize.Small)
        }
        ui.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = JustStudyTheme.colors.muted) }
    }
}

private val reviewedFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.US)

/** Your review history: the rating, the word (never the answer) and when it comes back. */
@Composable
private fun History(items: List<ReviewItem>) {
    val c = JustStudyTheme.colors
    Column(Modifier.fillMaxWidth().card().padding(Tokens.Space4)) {
        SectionTitle("Review history", "복습 기록")
        if (items.isEmpty()) {
            Spacer(Modifier.height(Tokens.Space2))
            Text("No reviews yet. Open a deck.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(thickness = Tokens.Border, color = c.line)
            Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
                val (bg, ink) = when (item.rating) {
                    "again" -> c.danger to Color.White
                    "hard" -> c.control to Color.White
                    "good" -> c.fix to c.onFix
                    else -> c.countNew to Color.White
                }
                Text(item.rating.replaceFirstChar { it.uppercase() }, Modifier.background(bg, CircleShape).padding(horizontal = Tokens.Space2, vertical = 2.dp), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = ink)
                Text(item.koreanWord, Modifier.weight(1f), style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val next = if (item.rating == "again") "relearning" else "next in ${hoursLabel(item.intervalHours)}"
                Text("$next · ${reviewedFormat.format(item.reviewedAt)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}

/** The web's hoursLabel. */
fun hoursLabel(hours: Long): String {
    if (hours < 48) return "$hours h"
    val days = Math.round(hours / 24.0)
    if (days < 30) return "$days d"
    if (days < 365) return "${Math.round(days / 30.0)} mo"
    return "${Math.round(days / 365.0)} y"
}

/** Review options (Again in minutes; Hard, Good and Easy in hours), as the web's ReviewOptions. */
@Composable
private fun OptionsSheet(current: Scheduling, onSave: (List<String>) -> String?, onDismiss: () -> Unit) {
    val fields = listOf(
        Triple("Again", "다시", "min") to "You forgot it: the card comes back after these minutes (in the same session if it's 20 min or less). E.g. 15.",
        Triple("Hard", "어려움", "hours") to "First time you rate a new card Hard, it comes back after these hours. After that, Hard grows the interval a little (×1.2).",
        Triple("Good", "알맞음", "hours") to "First time you rate a new card Good, it comes back after these hours (24 = one day). After that it grows with the card's ease (×2.5 by default).",
        Triple("Easy", "쉬움", "hours") to "First time you rate a new card Easy, it comes back after these hours (48 = two days). After that, Easy grows faster than Good.",
    )
    var values by remember { mutableStateOf(listOf(current.againMinutes, current.hardHours, current.goodHours, current.easyHours).map { it.toString() }) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    JsSheet(onDismiss) {
        Column(Modifier.padding(horizontal = Tokens.Space6), verticalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
            SectionTitle("Settings", "설정")
            fields.forEachIndexed { i, (f, help) ->
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(f.first, Modifier.alignByBaseline(), style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
                        KoSide(f.second, 14.sp, Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
                        JsTextField(values[i], { v -> values = values.toMutableList().also { it[i] = v.filter(Char::isDigit).take(4) } }, Modifier.width(120.dp))
                        Text(f.third, style = MaterialTheme.typography.bodySmall, color = JustStudyTheme.colors.muted)
                    }
                    Text(help, style = MaterialTheme.typography.bodySmall, color = JustStudyTheme.colors.muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
                StickerButton("Save", {
                    error = onSave(values); status = if (error == null) "Saved. New intervals apply from your next review." else null
                }, ko = "저장", color = StickerColor.Go, size = StickerSize.Small)
                StickerButton("Defaults", { val d = Scheduling(); values = listOf(d.againMinutes, d.hardHours, d.goodHours, d.easyHours).map { it.toString() } }, ko = "기본값", color = StickerColor.Gray, size = StickerSize.Small)
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = JustStudyTheme.colors.muted) }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = JustStudyTheme.colors.wrong) }
        }
    }
}

