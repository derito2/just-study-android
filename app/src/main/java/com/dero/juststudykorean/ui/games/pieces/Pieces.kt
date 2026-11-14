package com.dero.juststudykorean.ui.games.pieces

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.ui.components.KoSide
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.reduceMotion
import kotlin.math.roundToInt

/** A tile's states (games.md 6.1); never told by colour alone: correct and wrong carry an icon. */
enum class TileState { Normal, Selected, Correct, Wrong, Used }

private val TILE_SHAPE = RoundedCornerShape(12.dp)

/**
 * A tile: canvas, 2 dp border and a 3 dp edge below (StickerButton's language). [korean]: ko-title; else body-bold.
 * [pop] / [shake]: when the counter grows, the tile pops (right) or shakes (wrong), read in the draw phase.
 */
@Composable
fun Tile(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    korean: Boolean = true,
    state: TileState = TileState.Normal,
    pop: Int = 0,
    shake: Int = 0,
    minWidth: Dp = 48.dp,
    minHeight: Dp = 48.dp,
    maxLines: Int = 2,
    textStyle: androidx.compose.ui.text.TextStyle? = null,
    description: String? = null,
) {
    val c = JustStudyTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val still = LocalFrozenMotion.current || reduceMotion()
    val scale = remember { Animatable(1f) }
    val dx = remember { Animatable(0f) }
    LaunchedEffect(pop) { if (pop > 0 && !still) scale.animateTo(1f, keyframes { durationMillis = Motion.Pop; 1.1f at Motion.Pop / 2 }) }
    LaunchedEffect(shake) {
        if (shake > 0 && !still) dx.animateTo(0f, keyframes { durationMillis = Motion.Shake; -6f at 64; 6f at 128; -4f at 192; 4f at 256 })
    }
    val edge = if (pressed || state == TileState.Used) 0.dp else 3.dp
    val (fill, border) = when (state) {
        TileState.Selected -> c.tint to c.brand
        TileState.Correct -> c.correctFill to c.correctDeco
        TileState.Wrong -> c.wrongFill to c.wrongBorder
        else -> c.canvas to c.line
    }
    val stateText = when (state) { TileState.Selected -> ", selected"; TileState.Correct -> ", matched"; TileState.Wrong -> ", wrong"; else -> "" }
    Box(
        modifier
            .offset { IntOffset((dx.value * density).roundToInt(), if (edge == 0.dp) 3.dp.roundToPx() else 0) }
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .alpha(if (state == TileState.Used) 0.35f else 1f)
            .drawBehind { if (edge > 0.dp) drawRoundRect(c.field, topLeft = androidx.compose.ui.geometry.Offset(0f, 3.dp.toPx()), size = size, cornerRadius = CornerRadius(12.dp.toPx())) }
            .background(fill, TILE_SHAPE)
            .border(2.dp, border, TILE_SHAPE)
            .then(if (onClick != null) Modifier.selectable(state == TileState.Selected, interactionSource = source, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = (description ?: text) + stateText }
            .defaultMinSize(minWidth, minHeight)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = textStyle ?: if (korean) JustStudyTheme.korean.koTitle else JustStudyTheme.korean.bodyBold,
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        )
        if (state == TileState.Correct || state == TileState.Wrong) {
            LucideIcon(
                if (state == TileState.Correct) Lucide.Check else Lucide.X, contentDescription = null, size = 14.dp,
                tint = if (state == TileState.Correct) c.correct else c.wrong, modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-6).dp),
            )
        }
    }
}

/** A square syllable tile of 56 dp (48 with more than 6 in a row): ko-prompt. */
@Composable
fun BlockTile(block: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier, state: TileState = TileState.Normal, size: Dp = 56.dp, pop: Int = 0, shake: Int = 0, description: String? = null) =
    Tile(block, onClick, modifier, state = state, minWidth = size, minHeight = size, pop = pop, shake = shake, textStyle = JustStudyTheme.korean.koPrompt, maxLines = 1, description = description)

/** An empty slot: the size of the tile it waits for, a dashed 2 dp border (4 on, 4 off). [highlight]: a tile over it. */
@Composable
fun Slot(index: Int, total: Int, modifier: Modifier = Modifier, width: Dp = 56.dp, height: Dp = 56.dp, highlight: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = JustStudyTheme.colors
    val color = if (highlight) c.brand else c.field
    Box(
        modifier
            .size(width, height)
            .drawBehind {
                drawRoundRect(color, cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
            }
            .then(if (onClick != null) Modifier.selectable(false, onClick = onClick, role = Role.Button) else Modifier)
            .semantics { contentDescription = "Empty slot ${index + 1} of $total" },
    )
}

/** The instruction (one line and its Korean below) and, at its right, the optional hint pill with the meaning. */
@Composable
fun Instruction(text: String, ko: String, hint: String? = null, modifier: Modifier = Modifier) {
    val c = JustStudyTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(text, style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
            Text(ko, style = JustStudyTheme.korean.koBelow, color = c.muted)
        }
        if (hint != null) Hint(hint)
    }
}

@Composable
fun Hint(meaning: String, modifier: Modifier = Modifier) {
    val c = JustStudyTheme.colors
    Row(
        modifier.background(c.tint, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp).semantics { contentDescription = "Hint: $meaning" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(Lucide.Lightbulb, contentDescription = null, size = 14.dp, tint = c.brandText)
        Spacer(Modifier.width(4.dp))
        Text(meaning, style = JustStudyTheme.korean.chip, color = c.brandText, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Where the round's content comes from: the document without its extension (or "Extra · not from your notes"). */
@Composable
fun OriginMark(document: String?, modifier: Modifier = Modifier) {
    val text = document?.substringBeforeLast('.') ?: "Extra · not from your notes"
    Text(text, modifier, fontSize = 12.sp, color = JustStudyTheme.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** The game's name on the scene's bar: white, with its Korean in sky-light. */
@Composable
fun GameName(name: String, ko: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(name, Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
        KoSide(ko, MaterialTheme.typography.titleMedium.fontSize, Modifier.alignByBaseline(), color = com.dero.juststudykorean.ui.theme.Tokens.SkyLight)
    }
}
