package com.dero.juststudykorean.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.JustStudyColors
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.Tokens

enum class StickerColor { Action, Go, Fix, Danger, Gray }
enum class StickerSize { Default, Small }

private data class StickerPaint(val fill: Color, val pressed: Color, val edge: Color, val ink: Color)

private fun JustStudyColors.paint(color: StickerColor) = when (color) {
    StickerColor.Action -> StickerPaint(action, actionPressed, actionEdge, Color.White)
    StickerColor.Go -> StickerPaint(go, goPressed, goEdge, Color.White)
    StickerColor.Fix -> StickerPaint(fix, fixPressed, fixEdge, onFix)
    StickerColor.Danger -> StickerPaint(danger, dangerPressed, dangerEdge, Color.White)
    StickerColor.Gray -> StickerPaint(control, grayPressed, grayEdge, Color.White)
}

/**
 * Duolingo "sticker" button: solid fill and a solid edge below that sinks when pressed (90 ms). No ripple.
 * Buttons keep their colors in dark mode.
 */
@Composable
fun StickerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ko: String? = null,
    color: StickerColor = StickerColor.Action,
    size: StickerSize = StickerSize.Default,
    enabled: Boolean = true,
) = StickerButton(text, onClick, modifier, ko, color, size, enabled, forcePressed = false)

/** [forcePressed] only for screenshot tests of the pressed state. */
@Composable
internal fun StickerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    ko: String?,
    color: StickerColor,
    size: StickerSize,
    enabled: Boolean,
    forcePressed: Boolean,
) {
    val colors = JustStudyTheme.colors
    val paint = colors.paint(color)
    val source = remember { MutableInteractionSource() }
    val pressed = enabled && (forcePressed || source.collectIsPressedAsState().value)
    val edge = if (size == StickerSize.Default) Tokens.EdgeLg else Tokens.EdgeMd
    val height = if (size == StickerSize.Default) Tokens.ButtonH else Tokens.ButtonSmH
    val sink by animateDpAsState(if (pressed) edge else 0.dp, tween(Motion.Press, easing = Motion.EaseOut), label = "sink")
    val fill by animateColorAsState(
        when { !enabled -> colors.disabledFill; pressed -> paint.pressed; else -> paint.fill },
        tween(Motion.Press, easing = Motion.EaseOut), label = "fill",
    )
    val ink = if (enabled) paint.ink else colors.disabledInk
    val shape = MaterialTheme.shapes.medium
    val label = if (size == StickerSize.Default) MaterialTheme.typography.labelLarge else JustStudyTheme.korean.buttonSm

    Box(
        modifier
            .then(if (size == StickerSize.Small) Modifier.minimumInteractiveComponentSize() else Modifier)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(bottom = edge), // room for the edge: the button is height + edge tall
        propagateMinConstraints = true, // fillMaxWidth() on the button stretches the face
    ) {
        // Edge: the same shape, `edge` dp lower; hidden while pressed and when disabled.
        if (enabled && !pressed) Box(Modifier.matchParentSize().offset(y = edge).background(paint.edge, shape))
        Column(
            Modifier
                .offset(y = sink)
                .background(fill, shape)
                .heightIn(min = height)
                .padding(horizontal = if (size == StickerSize.Default) Tokens.Space4 else Tokens.Space3, vertical = Tokens.Space2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text.uppercase(), color = ink, style = label, textAlign = TextAlign.Center)
            if (ko != null) {
                Spacer(Modifier.height(1.dp))
                Text(ko, color = ink, style = JustStudyTheme.korean.koBelow, modifier = Modifier.alpha(0.85f), textAlign = TextAlign.Center)
            }
        }
    }
}
