package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/** − n + (design/quiz.md A.2): Gray Small buttons 48 dp wide with `minus`/`plus`, the number at 24 sp; [range] limits. */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, range: IntRange, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
        StickerButton("Fewer", { onChange(value - 1) }, Modifier.width(48.dp), color = StickerColor.Gray, size = StickerSize.Small, enabled = value > range.first, icon = Lucide.Minus)
        Text("$value", Modifier.widthIn(min = 28.dp).semantics { contentDescription = "$label: $value" }, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        StickerButton("More", { onChange(value + 1) }, Modifier.width(48.dp), color = StickerColor.Gray, size = StickerSize.Small, enabled = value < range.last, icon = Lucide.Plus)
        Text("${range.first} to ${range.last}", style = MaterialTheme.typography.bodySmall, color = JustStudyTheme.colors.muted)
    }
}
