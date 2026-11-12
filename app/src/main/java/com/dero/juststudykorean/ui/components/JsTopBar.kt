package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens
import kotlin.math.roundToInt

/**
 * Top bar of every destination (design/shell.md, `shell-topbar-*`): a 64 dp row with `page-title` and its Korean
 * (55 %, 60 % opacity) and More (`menu`, 48 dp); below, the `muted` subtitle and its Korean. Scrolling folds the
 * subtitle away and the title becomes `section-title` in the 64 dp row. Second-level screens get `chevron-left`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsTopBar(
    title: String,
    ko: String,
    subtitle: String?,
    subtitleKo: String?,
    scrollBehavior: TopAppBarScrollBehavior,
    onMore: (() -> Unit)?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    forceFolded: Boolean = false,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val state = scrollBehavior.state
    val folded = forceFolded || state.collapsedFraction > 0.5f || (subtitle == null && subtitleKo == null && state.overlappedFraction > 0f)
    var moreTaps by remember { mutableIntStateOf(0) }
    var backTaps by remember { mutableIntStateOf(0) }
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = if (onBack != null) Tokens.Space1 else Tokens.Space4, end = Tokens.Space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = { backTaps++; onBack() }) {
                    LucideIcon(Lucide.ChevronLeft, contentDescription = "Back", tint = ink, animateKey = backTaps)
                }
            }
            Box(Modifier.weight(1f)) {
                if (folded) {
                    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(title, Modifier.alignByBaseline().weight(1f, fill = false).semantics { heading() }, style = MaterialTheme.typography.headlineLarge, color = ink)
                        KoSide(ko, MaterialTheme.typography.headlineLarge.fontSize, Modifier.alignByBaseline(), ratio = 0.55f, color = ink.copy(alpha = 0.6f))
                    }
                }
            }
            if (onMore != null) {
                IconButton(onClick = { moreTaps++; onMore() }) {
                    LucideIcon(Lucide.Menu, contentDescription = "More", tint = ink, animateKey = moreTaps)
                }
            } else {
                Spacer(Modifier.width(Tokens.Space3))
            }
        }
        if (subtitle != null || subtitleKo != null) {
            // The subtitle block folds with the scroll: its height is the scroll behaviour's limit.
            Layout(
                content = {
                    Column(Modifier.padding(start = Tokens.Space4, end = Tokens.Space4, bottom = Tokens.Space4)) {
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = muted)
                        if (subtitleKo != null) Text(subtitleKo, style = JustStudyTheme.korean.koBelow, color = muted)
                    }
                },
                modifier = Modifier.clipToBounds(),
            ) { measurables, constraints ->
                val block = measurables.first().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                state.heightOffsetLimit = -block.height.toFloat()
                val height = (block.height + state.heightOffset).roundToInt().coerceIn(0, block.height)
                layout(constraints.maxWidth, height) { block.place(0, height - block.height) }
            }
        }
    }
}
