package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.ui.theme.JustStudyTheme

/**
 * AiMark (design/shell.md): on everything that doesn't come from your notes. [short]: only "AI" (the quiz judge and
 * Words detected). [fromNotes]: the model wrote it from your notes and cites them (R1-11). Not animated, no new colors.
 */
@Composable
fun AiMark(modifier: Modifier = Modifier, short: Boolean = false, fromNotes: Boolean = false) {
    val label = if (fromNotes) "from your notes" else "not from your notes"
    val colors = JustStudyTheme.colors
    Row(
        modifier
            .clearAndSetSemantics { contentDescription = "AI, $label" }
            .background(colors.canvas, CircleShape)
            .border(1.dp, colors.line, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(Lucide.Sparkles, contentDescription = null, size = 12.dp, tint = colors.muted)
        Spacer(Modifier.width(4.dp))
        Text(if (short) "AI" else "AI · $label", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.muted)
    }
}
