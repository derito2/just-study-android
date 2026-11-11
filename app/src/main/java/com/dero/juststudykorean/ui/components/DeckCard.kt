package com.dero.juststudykorean.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import com.dero.juststudykorean.ui.theme.Tokens

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
) {
    val angle = if (wiggle) {
        val t = rememberInfiniteTransition(label = "wiggle")
        t.animateFloat(-0.6f, 0.6f, infiniteRepeatable(tween(140), RepeatMode.Reverse, StartOffset(if (alternate) 140 else 0)), label = "angle").value
    } else 0f
    // Only this thin shell recomposes on every wiggle frame: the card itself is skipped (same arguments).
    DeckCardBody(deck, onContinue, onDelete, modifier.rotate(angle), editing, dragged, mark, onRename)
}

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
    Column(
        modifier
            .alpha(if (dragged) 0.5f else 1f)
            .drawBehind {
                if (mark == DropMark.Before || mark == DropMark.After) {
                    val x = if (mark == DropMark.Before) -6.dp.toPx() else size.width + 6.dp.toPx()
                    drawLine(colors.brand, Offset(x, 0f), Offset(x, size.height), 4.dp.toPx())
                }
            }
            .border(if (mark == DropMark.On) 4.dp else Tokens.Border, ring, MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .padding(Tokens.Space3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.Space2),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDelete, Modifier.size(48.dp).padding(0.dp)) {
                LucideIcon(Lucide.Trash2, contentDescription = "Delete ${deck.name}", size = 20.dp, tint = colors.danger)
            }
            Spacer(Modifier.weight(1f))
            if (deck.custom) {
                Box(Modifier.size(24.dp).background(colors.tint, CircleShape).semantics { contentDescription = "Made by Teacher" }, contentAlignment = Alignment.Center) {
                    LucideIcon(Lucide.GraduationCap, contentDescription = null, size = 16.dp, tint = colors.brandText)
                }
                Spacer(Modifier.width(Tokens.Space1))
            }
            Text(
                "${deck.total} cards",
                Modifier.background(colors.canvas, CircleShape).padding(horizontal = Tokens.Space2, vertical = 2.dp),
                style = JustStudyTheme.korean.chip, color = colors.muted, maxLines = 1,
            )
        }
        if (editing) {
            var name by remember(deck.name) { mutableStateOf(deck.name) }
            var hadFocus by remember { mutableStateOf(false) }
            val save = { onRename(name.trim().ifEmpty { deck.name }.also { if (name.isBlank()) name = deck.name }) }
            androidx.compose.foundation.text.BasicTextField(
                name, { if (it.length <= 120) name = it },
                Modifier.fillMaxWidth().semantics { contentDescription = "Name of ${deck.name}" }
                    .drawBehind { drawLine(colors.field, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) { hadFocus = false; save() } },
                singleLine = true,
                textStyle = JustStudyTheme.korean.bodyBold.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { save() }),
            )
        } else {
            Text(deck.name, style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Count(deck.new, "New", colors.countNew, Modifier.weight(1f))
            Box(Modifier.width(2.dp).fillMaxHeight().background(colors.line))
            Count(deck.learning, "Study", colors.fix, Modifier.weight(1f))
            Box(Modifier.width(2.dp).fillMaxHeight().background(colors.line))
            Count(deck.due, "Due", colors.wrong, Modifier.weight(1f))
        }
        val word = deck.lastCard?.koreanWord
        if (word != null) {
            Text(word, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text("Not studied yet.", Modifier.padding(vertical = 5.dp), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        }
        val ready = deck.availableNow > 0
        StickerButton(
            if (ready) "Continue" else "Nothing due", onContinue, Modifier.fillMaxWidth(),
            ko = if (ready) "계속하기" else "복습 없음", size = StickerSize.Small, enabled = ready && !editing,
        )
    }
}

@Composable
private fun Count(n: Int, label: String, color: Color, modifier: Modifier) {
    Column(modifier.clearAndSetSemantics { contentDescription = "$n $label" }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$n", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = color)
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = JustStudyTheme.colors.muted, maxLines = 1)
    }
}
