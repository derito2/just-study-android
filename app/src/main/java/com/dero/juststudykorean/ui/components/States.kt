package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens
import kotlinx.coroutines.delay

@Composable
private fun Modifier.card() = this
    .fillMaxWidth()
    .border(Tokens.Border, JustStudyTheme.colors.line, MaterialTheme.shapes.large)
    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
    .padding(Tokens.Space4)

/** Empty: centered card, 40 dp icon (animates once when shown), title with Korean, one `muted` line, `Go` action. */
@Composable
fun EmptyState(
    icon: Lucide,
    title: String,
    ko: String?,
    message: String?,
    modifier: Modifier = Modifier,
    action: String? = null,
    actionKo: String? = null,
    onAction: () -> Unit = {},
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Column(modifier.card(), horizontalAlignment = Alignment.CenterHorizontally) {
        LucideIcon(icon, contentDescription = null, size = 40.dp, tint = JustStudyTheme.colors.control, animateKey = shown)
        Spacer(Modifier.height(Tokens.Space3))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (ko != null) KoSide(ko, MaterialTheme.typography.titleMedium.fontSize, Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.onSurface)
        }
        if (message != null) {
            Spacer(Modifier.height(Tokens.Space1))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(Tokens.Space4))
            StickerButton(action, onAction, ko = actionKo, color = StickerColor.Go)
        }
    }
}

/** Loading: the usual text and a plain indeterminate bar (no M3 gap or stop dot, as in the design), shown only after 300 ms. [progress] fixes the bar (tests). */
@Composable
fun LoadingState(text: String = "Loading…", modifier: Modifier = Modifier) = LoadingState(text, modifier, delayMs = 300, progress = null)

@Composable
internal fun LoadingState(text: String, modifier: Modifier, delayMs: Long, progress: Float?) {
    var visible by remember { mutableStateOf(delayMs == 0L) }
    LaunchedEffect(Unit) { delay(delayMs); visible = true }
    if (!visible) return
    Column(modifier.card(), verticalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val colors = JustStudyTheme.colors
        if (progress == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.brand, trackColor = colors.tint, gapSize = 0.dp)
        } else {
            LinearProgressIndicator({ progress }, Modifier.fillMaxWidth(), color = colors.brand, trackColor = colors.tint, gapSize = 0.dp, drawStopIndicator = {})
        }
    }
}

/** Error: `wrong-fill` box, `badge-alert` (shakes once), what happened, and Try again (Gray, Small). */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier, title: String? = null, retryLabel: String = "Try again") {
    val colors = JustStudyTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Row(
        modifier.fillMaxWidth().background(colors.wrongFill, MaterialTheme.shapes.medium).padding(Tokens.Space4),
    ) {
        LucideIcon(Lucide.BadgeAlert, contentDescription = null, size = 20.dp, tint = colors.wrong, animateKey = shown)
        Spacer(Modifier.width(Tokens.Space3))
        Column {
            if (title != null) Text(title, style = JustStudyTheme.korean.bodyBold, color = colors.wrong)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.wrong)
            Spacer(Modifier.height(Tokens.Space2))
            StickerButton(retryLabel, onRetry, color = StickerColor.Gray, size = StickerSize.Small)
        }
    }
}
