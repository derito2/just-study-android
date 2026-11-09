package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/**
 * TextField (design system): 2 dp border (`line`, `brand` while focused, or [borderColor]), radius 12, one line.
 * [onDone]: Enter. [leading]/[trailing]: icons inside.
 */
@Composable
fun JsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    center: Boolean = false,
    borderColor: Color? = null,
    enabled: Boolean = true,
    onDone: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = JustStudyTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val ink = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value, onValueChange, modifier,
        enabled = enabled, singleLine = true, interactionSource = source,
        textStyle = style.copy(color = ink, textAlign = if (center) TextAlign.Center else TextAlign.Start),
        cursorBrush = SolidColor(colors.brand),
        keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .border(Tokens.Border, borderColor ?: if (focused) colors.brand else colors.line, MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                    .padding(horizontal = Tokens.Space4, vertical = Tokens.Space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) { leading(); Spacer(Modifier.width(Tokens.Space2)) }
                Box(Modifier.weight(1f), contentAlignment = if (center) Alignment.Center else Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, style = style, color = colors.muted, textAlign = if (center) TextAlign.Center else TextAlign.Start)
                    inner()
                }
                if (trailing != null) trailing()
            }
        },
    )
}

