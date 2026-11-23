package com.dero.juststudykorean.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.Role
import com.dero.juststudykorean.ui.theme.reduceMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.data.flashcards.Deck
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.dero.juststudykorean.ui.theme.Tokens

/** design/ajustes.md AJ-28: every deck has one height (measured in flashcards_decks: a deck with a one-line name). */
internal val DeckGridHeight = 230.dp
internal val DeckRowHeight = 72.dp

/** AJ-28: from 130 % text the fixed height grows in the same proportion, so nothing is cut. */
@Composable
private fun fixedHeight(base: Dp): Dp = LocalDensity.current.fontScale.let { if (it >= 1.3f) base * it else base }

/** Where a dragged deck would land on this one: on it (combine) or at its start/end edge (move). */
enum class DropMark { None, On, Before, After }

/**
 * DeckCard (design/flashcards.md A.4): trash, Teacher mark and "{n} cards"; the name; New · Study · Due; the last
 * word in Korean; Continue. In edit mode the card wiggles, the name is a field and Continue is off.
 */
@Composable
fun DeckCard(
    deck: Deck,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    wiggle: Boolean = false,
    alternate: Boolean = false,
    dragged: Boolean = false,
    mark: DropMark = DropMark.None,
    onRename: (String) -> Unit = {},
    asRow: Boolean = false,
) {
    val angle = when {
        !wiggle -> 0f
        LocalFrozenMotion.current -> -0.6f // the first frame
        else -> {
            val t = rememberInfiniteTransition(label = "wiggle")
            t.animateFloat(-0.6f, 0.6f, infiniteRepeatable(tween(140), RepeatMode.Reverse, StartOffset(if (alternate) 140 else 0)), label = "angle").value
        }
    }
    // Only this thin shell recomposes on every wiggle frame: the card itself is skipped (same arguments).
    if (asRow) DeckRowBody(deck, onContinue, onDelete, modifier.rotate(angle), editing, dragged, mark, onRename)
    else DeckCardBody(deck, onContinue, onDelete, modifier.rotate(angle), editing, dragged, mark, onRename)
}

/**
 * AJ-27: one deck as a full-width row: name and size, New · Study · Due, (the last word from 560 dp), an arrow.
 * The whole row opens the deck ([onOpen]) and sinks 2 dp while pressed; while editing it does not open.
 */
@Composable
private fun DeckRowBody(
    deck: Deck,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
    editing: Boolean,
    dragged: Boolean,
    mark: DropMark,
    onRename: (String) -> Unit,
) {
    val colors = JustStudyTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = reduceMotion()
    val sink by animateDpAsState(if (pressed && !editing) 2.dp else 0.dp, if (reduce) snap() else tween(90), label = "sink")
    Box(
        modifier
            .offset(y = sink)
            .alpha(if (dragged) 0.5f else 1f)
            .drawBehind {
                if (mark == DropMark.Before || mark == DropMark.After) {
                    val x = if (mark == DropMark.Before) -6.dp.toPx() else size.width + 6.dp.toPx()
                    drawLine(colors.brand, Offset(x, 0f), Offset(x, size.height), 4.dp.toPx())
                }
            }
            .height(fixedHeight(DeckRowHeight))
            .clip(MaterialTheme.shapes.large)
            .border(if (mark == DropMark.On) 4.dp else Tokens.Border, if (mark == DropMark.On) colors.brand else colors.line, MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .then(
                if (editing) Modifier
                else Modifier.clickable(source, indication = null, role = Role.Button, onClick = onOpen).semantics { contentDescription = "Open ${deck.name}" },
            ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(Tokens.Space3)) {
            val wide = maxWidth >= 560.dp
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
                    if (editing) RenameField(deck, onRename, Modifier, TextAlign.Start, JustStudyTheme.korean.bodyBold)
                    else DeckName(deck.name, JustStudyTheme.korean.bodyBold, TextAlign.Start, maxChars = 24)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // In a row the corner has no room for the mark (it would cover Due), so it stays here while editing.
                        if (deck.custom) { TeacherMark(); Spacer(Modifier.width(Tokens.Space1)) }
                        Pill("${deck.total} cards", PillTone.Neutral)
                    }
                }
                RowDivider()
                Count(deck.new, "New", colors.countNew, Modifier.width(44.dp))
                Count(deck.learning, "Study", colors.fix, Modifier.width(44.dp))
                Count(deck.due, "Due", colors.wrongInk, Modifier.width(44.dp))
                RowDivider()
                if (wide) {
                    val word = deck.lastCard?.koreanWord
                    Text(
                        word ?: "Not studied yet.", Modifier.weight(1f).padding(horizontal = Tokens.Space2),
                        style = if (word != null) JustStudyTheme.korean.bodyBold else MaterialTheme.typography.bodyMedium,
                        color = if (word != null) MaterialTheme.colorScheme.onSurface else colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                // The arrow is only a sign (the row is the target); while editing it hides and keeps its room.
                Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                    if (!editing) LucideIcon(Lucide.ChevronLeft, contentDescription = null, size = 24.dp, tint = if (deck.availableNow > 0) colors.control else colors.disabledInk, modifier = Modifier.rotate(180f))
                }
            }
        }
        if (editing) DeleteCorner(deck, onDelete, Modifier.align(Alignment.TopEnd), mark = false)
    }
}

/**
 * AJ-29: the deck's name on one line. If it does not fit it scrolls by itself (24 dp/s, 1.5 s before the first pass,
 * 2 s between passes); with reduce motion it stands still, shortened in the middle (P-04). TalkBack reads it whole.
 */
@Composable
private fun DeckName(name: String, style: TextStyle, align: TextAlign, maxChars: Int) {
    val still = reduceMotion()
    val frozen = LocalFrozenMotion.current // screenshot tests: the marquee's first frame, without an endless animation
    Text(
        displayName(name, if (still) maxChars else Int.MAX_VALUE),
        Modifier.fillMaxWidth()
            .then(if (still || frozen) Modifier else Modifier.basicMarquee(Int.MAX_VALUE, repeatDelayMillis = 2000, initialDelayMillis = 1500, velocity = 24.dp))
            .semantics { contentDescription = name },
        style = style, color = MaterialTheme.colorScheme.onSurface, textAlign = align, maxLines = 1,
        softWrap = false, overflow = if (still) TextOverflow.Ellipsis else TextOverflow.Clip,
    )
}

@Composable
private fun RowDivider() = Box(Modifier.padding(horizontal = Tokens.Space2).width(2.dp).height(40.dp).background(JustStudyTheme.colors.line))

@Composable
private fun DeckCardBody(
    deck: Deck,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
    editing: Boolean,
    dragged: Boolean,
    mark: DropMark,
    onRename: (String) -> Unit,
) {
    val colors = JustStudyTheme.colors
    val ring = when (mark) { DropMark.On -> colors.brand; else -> colors.line }
    Box(
        modifier
            .alpha(if (dragged) 0.5f else 1f)
            .drawBehind {
                if (mark == DropMark.Before || mark == DropMark.After) {
                    val x = if (mark == DropMark.Before) -6.dp.toPx() else size.width + 6.dp.toPx()
                    drawLine(colors.brand, Offset(x, 0f), Offset(x, size.height), 4.dp.toPx())
                }
            }
            .height(fixedHeight(DeckGridHeight))
            .clip(MaterialTheme.shapes.large) // what does not fit is cut, never grows the deck
            .border(if (mark == DropMark.On) 4.dp else Tokens.Border, ring, MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Tokens.Space3),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Tokens.Space2),
        ) {
            // P-25: the name leads, then the size. AJ-26: while editing the name is a field, clear of the corner cross.
            if (editing) {
                RenameField(deck, onRename, Modifier.padding(start = 36.dp, end = if (deck.custom) 60.dp else 36.dp), TextAlign.Center)
            } else {
                DeckName(deck.name, MaterialTheme.typography.titleMedium, TextAlign.Center, maxChars = 18)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (deck.custom && !editing) {
                    TeacherMark()
                    Spacer(Modifier.width(Tokens.Space1))
                }
                Pill("${deck.total} cards", PillTone.Neutral)
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Count(deck.new, "New", colors.countNew, Modifier.weight(1f))
                Box(Modifier.width(2.dp).fillMaxHeight().background(colors.line))
                Count(deck.learning, "Study", colors.fix, Modifier.weight(1f))
                Box(Modifier.width(2.dp).fillMaxHeight().background(colors.line))
                Count(deck.due, "Due", colors.wrongInk, Modifier.weight(1f))
            }
            val word = deck.lastCard?.koreanWord
            if (word != null) {
                Text(word, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text("Not studied yet.", Modifier.padding(vertical = 5.dp), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            // AJ-26: while editing, Continue is hidden but keeps its room, so the deck does not change size.
            val ready = deck.availableNow > 0
            StickerButton(
                if (ready) "Continue" else "Nothing due", if (editing) ({}) else onContinue,
                Modifier.fillMaxWidth().then(if (editing) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier),
                ko = if (ready) "계속하기" else "복습 없음", size = StickerSize.Small, enabled = ready && !editing,
            )
        }
        if (editing) DeleteCorner(deck, onDelete, Modifier.align(Alignment.TopEnd))
    }
}

/** AJ-26: the deck's name as a one-line field; saved on Done or when it loses focus (an empty name keeps the old one). */
@Composable
private fun RenameField(deck: Deck, onRename: (String) -> Unit, modifier: Modifier, align: TextAlign, style: TextStyle = MaterialTheme.typography.titleMedium) {
    val colors = JustStudyTheme.colors
    var name by remember(deck.name) { mutableStateOf(deck.name) }
    var hadFocus by remember { mutableStateOf(false) }
    val save = { onRename(name.trim().ifEmpty { deck.name }.also { if (name.isBlank()) name = deck.name }) }
    androidx.compose.foundation.text.BasicTextField(
        name, { if (it.length <= 120) name = it },
        modifier.fillMaxWidth().semantics { contentDescription = "Name of ${deck.name}" }
            .drawBehind { drawLine(colors.field, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) { hadFocus = false; save() } },
        singleLine = true,
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = align),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { save() }),
    )
}

@Composable
private fun TeacherMark() {
    val colors = JustStudyTheme.colors
    Box(Modifier.size(24.dp).background(colors.tint, CircleShape).semantics { contentDescription = "Made by Teacher" }, contentAlignment = Alignment.Center) {
        LucideIcon(Lucide.GraduationCap, contentDescription = null, size = 16.dp, tint = colors.brandText)
    }
}

/** AJ-26: the cross in the deck's top right corner (20 dp, 48 dp target); a Teacher deck's mark sits to its left. */
@Composable
private fun DeleteCorner(deck: Deck, onDelete: () -> Unit, modifier: Modifier, mark: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (deck.custom && mark) TeacherMark()
        IconButton(onClick = onDelete, Modifier.size(48.dp)) {
            LucideIcon(Lucide.X, contentDescription = "Delete ${deck.name}", size = 20.dp, tint = JustStudyTheme.colors.wrongInk)
        }
    }
}

@Composable
private fun Count(n: Int, label: String, color: Color, modifier: Modifier) {
    Column(modifier.clearAndSetSemantics { contentDescription = "$n $label" }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$n", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = color)
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.5.sp), color = JustStudyTheme.colors.muted, maxLines = 1)
    }
}
