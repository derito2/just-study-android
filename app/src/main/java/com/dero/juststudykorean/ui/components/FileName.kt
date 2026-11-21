package com.dero.juststudykorean.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/** design/polish.md P-04: a file name on up to [maxLines] lines, without `.pdf` and shortened in the middle. TalkBack reads it whole. */
@Composable
fun FileName(
    name: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 2,
    textAlign: TextAlign? = null,
) {
    Text(
        displayName(name, maxLines * CHARS_PER_LINE), modifier.semantics { contentDescription = name },
        style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = textAlign,
    )
}

/** About what fits in a line of a card at 411 dp; the Text's own ellipsis is only the safety net. */
private const val CHARS_PER_LINE = 22
