package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/** One quiz in the history: score, where its questions came from and when. */
data class HistoryLine(val key: String, val correct: Int, val total: Int, val title: String, val date: String, val leftEarly: Boolean = false)

/** Quiz history (design/quiz.md A.4): a 44 dp ring with the score, the document and the date, and Details. */
@Composable
fun QuizHistoryCard(lines: List<HistoryLine>, onDetails: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = JustStudyTheme.colors
    Column(modifier.fillMaxWidth().border(Tokens.Border, c.line, MaterialTheme.shapes.large).padding(Tokens.Space4)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Quiz history", Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            KoSide("퀴즈 기록", MaterialTheme.typography.titleMedium.fontSize, Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.onSurface)
        }
        if (lines.isEmpty()) Text("No quizzes yet. Take one.", Modifier.padding(top = Tokens.Space2), style = MaterialTheme.typography.bodyMedium, color = c.muted)
        lines.forEachIndexed { i, l ->
            HorizontalDivider(Modifier.padding(top = if (i == 0) Tokens.Space2 else 0.dp), thickness = Tokens.Border, color = c.line)
            Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
                val ratio = if (l.total == 0) 0.0 else l.correct.toDouble() / l.total
                val ring = when { ratio >= 0.8 -> c.correctStrong; ratio >= 0.5 -> c.fix; else -> c.wrong }
                Box(Modifier.size(44.dp).border(3.dp, ring, CircleShape).clearAndSetSemantics { contentDescription = "${l.correct} of ${l.total} correct" }, contentAlignment = Alignment.Center) {
                    Text("${l.correct}/${l.total}", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurface)
                }
                Column(Modifier.weight(1f)) {
                    Text(l.title, style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (l.leftEarly) "${l.date} · Left early" else l.date, style = MaterialTheme.typography.bodySmall, color = c.muted) // R1-08
                }
                StickerButton("Details", { onDetails(l.key) }, ko = "자세히", color = StickerColor.Gray, size = StickerSize.Small)
            }
        }
    }
}
