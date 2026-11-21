package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.DarkJustStudyColors
import com.dero.juststudykorean.ui.theme.JustStudyTheme

/** design/polish.md P-02: the tone of a [Pill]; each has its own fill and ink (nothing from the theme's `onSurface`). */
enum class PillTone { Neutral, Info, Correct, Wrong, Warn }

/**
 * The one small label: 24 dp tall, full radius, 2 × 10 dp padding, `chip` text, an optional 12 dp icon. In dark, `canvas` is
 * the background colour, so a Neutral pill gets a 1 dp `line` border to stay a pill.
 */
@Composable
fun Pill(text: String, tone: PillTone = PillTone.Neutral, modifier: Modifier = Modifier, icon: Lucide? = null) {
    val c = JustStudyTheme.colors
    val (fill, ink) = when (tone) {
        PillTone.Neutral -> c.canvas to c.muted
        PillTone.Info -> c.tint to c.brandText
        PillTone.Correct -> c.correctFill to c.correctInk
        PillTone.Wrong -> c.wrongFill to c.wrongInk
        PillTone.Warn -> c.warnFill to c.fillWarn
    }
    val border = if (tone == PillTone.Neutral && c === DarkJustStudyColors) Modifier.border(1.dp, c.line, CircleShape) else Modifier
    Row(
        modifier.heightIn(min = 24.dp).background(fill, CircleShape).then(border).padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            LucideIcon(icon, contentDescription = null, size = 12.dp, tint = ink)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = JustStudyTheme.korean.chip, color = ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}
