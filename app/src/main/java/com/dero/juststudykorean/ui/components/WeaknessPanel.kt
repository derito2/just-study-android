package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dero.juststudykorean.R
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/** One mistake as the panel shows it: the document and "{date} · {type} · wrong {n}×". Never the question or the answer. */
data class MistakeLine(val document: String, val detail: String)

/** Quiz for your weaknesses (design/quiz.md A.3) on `flags`, under `photo-veil-strong`. [mistakes] null while loading. */
@Composable
fun WeaknessPanel(mistakes: List<MistakeLine>?, onFix: () -> Unit, modifier: Modifier = Modifier, photo: Boolean = true) {
    val c = JustStudyTheme.colors
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.RadiusMd))) {
        if (photo) Image(painterResource(R.drawable.flags), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, 0.30f * 2 - 1))
        Box(Modifier.matchParentSize().background(if (photo) c.photoVeilStrong else c.heroIndigo))
        Column(Modifier.padding(Tokens.Space4), verticalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
            Row(Modifier.semantics { heading() }, verticalAlignment = Alignment.Bottom) {
                Text("Quiz for your weaknesses", Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, color = Color.White)
                KoSide("약점 퀴즈", MaterialTheme.typography.titleMedium.fontSize, Modifier.alignByBaseline(), color = Color.White)
            }
            when {
                mistakes == null -> Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                mistakes.isEmpty() -> Text("No mistakes to fix. For now.", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                else -> {
                    Text("Questions you got wrong and haven't fixed yet, from all your quizzes.", style = MaterialTheme.typography.bodySmall, color = c.onHeroSoft)
                    mistakes.take(5).forEach { m ->
                        Column(Modifier.fillMaxWidth().background(c.onPhotoItem, MaterialTheme.shapes.small).padding(horizontal = Tokens.Space3, vertical = Tokens.Space2)) {
                            Text(m.document, style = JustStudyTheme.korean.bodyBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(m.detail, style = MaterialTheme.typography.bodySmall, color = c.onHeroSoft)
                        }
                    }
                }
            }
            StickerButton("Fix these weaknesses", onFix, Modifier.fillMaxWidth(), ko = "약점 고치기", color = StickerColor.Fix, enabled = !mistakes.isNullOrEmpty())
        }
    }
}
