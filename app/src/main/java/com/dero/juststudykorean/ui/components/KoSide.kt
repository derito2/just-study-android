package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * `ko-side` (design/shell.md, as in the web): Korean next to an English text, [ratio] of its size (0.75, or 0.55
 * in the page title), weight 700, 0.45 em after it, no tracking, system font.
 */
@Composable
internal fun KoSide(text: String, baseSize: TextUnit, modifier: Modifier = Modifier, ratio: Float = 0.75f, color: Color = LocalContentColor.current) {
    val size = (baseSize.value * ratio).sp
    val gap = with(LocalDensity.current) { (size * 0.45f).toDp() }
    Text(
        text,
        modifier.padding(start = gap),
        color = color,
        maxLines = 1,
        style = TextStyle(fontFamily = FontFamily.Default, fontSize = size, fontWeight = FontWeight.Bold, letterSpacing = 0.em),
    )
}
