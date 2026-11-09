package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.JustStudyTheme

/** The 18 dp box (design/quiz.md): checked `action` with a white `check`; unchecked, a 2 dp `control` border. The row is the touch area. */
@Composable
fun JsCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = JustStudyTheme.colors
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier.size(18.dp).then(if (checked) Modifier.background(colors.action, shape) else Modifier.border(2.dp, colors.control, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) LucideIcon(Lucide.Check, contentDescription = null, size = 14.dp, strokeWidth = 3f, tint = Color.White)
    }
}
