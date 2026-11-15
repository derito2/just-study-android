package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.DarkJustStudyColors
import com.dero.juststudykorean.ui.theme.JustStudyTheme

/**
 * design/polish.md P-03: a `canvas` box (radius 12). In dark `canvas` is the same colour as the background, so the box gets a
 * 2 dp `line` border (the cards' own) and is still a box; in light the fill is enough.
 */
@Composable
fun Modifier.softBox(): Modifier {
    val c = JustStudyTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val filled = background(c.canvas, shape)
    return if (c === DarkJustStudyColors) filled.border(2.dp, c.line, shape) else filled
}
