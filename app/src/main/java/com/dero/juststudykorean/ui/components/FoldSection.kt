package com.dero.juststudykorean.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Motion

/**
 * M-8 (design/polish.md P-06, used by P-30): a section that folds. Closed it shows its one-line [summary] ("7 of 9 selected");
 * the chevron turns 180° and the height changes in 240 ms (ease-in-out). `chevron-left` at −90° points down, at 90° up.
 */
@Composable
fun FoldSection(
    title: String,
    ko: String?,
    summary: String,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = JustStudyTheme.colors
    val turn = animateFloatAsState(if (open) 90f else -90f, tween(Motion.Base, easing = Motion.EaseInOut), label = "chevron")
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = if (open) "Collapse $title" else "Expand $title", onClick = onToggle)
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(title, Modifier.alignByBaseline(), style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
                    if (ko != null) KoSide(ko, 14.sp, Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.onSurface)
                }
                if (!open) Text(summary, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
            }
            LucideIcon(Lucide.ChevronLeft, contentDescription = null, Modifier.graphicsLayer { rotationZ = turn.value }, size = 20.dp, tint = c.muted)
        }
        AnimatedVisibility(
            open,
            enter = expandVertically(tween(Motion.Base, easing = Motion.EaseInOut)),
            exit = shrinkVertically(tween(Motion.Base, easing = Motion.EaseInOut)),
        ) { Column { content() } }
    }
}
