package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.R
import com.dero.juststudykorean.data.progress.Recommendation
import com.dero.juststudykorean.data.progress.Today
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/**
 * Today's session (design/dashboard.md §2) on your photo with the `photo-veil`; without [photo], the hero gradient.
 * [today] and [recommended] null while loading. Text is white.
 */
@Composable
fun TodayHero(
    today: Today?,
    recommended: List<Recommendation>?,
    onPractice: (String) -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
    photo: Boolean = true,
    notice: String? = null,
) {
    val colors = JustStudyTheme.colors
    val shape = RoundedCornerShape(Tokens.RadiusMd)
    Box(modifier.fillMaxWidth().clip(shape)) {
        if (photo) {
            Image(
                painterResource(R.drawable.dashboard), contentDescription = null,
                modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, 0.72f * 2 - 1), // CSS "center 72%"
            )
            Box(Modifier.matchParentSize().background(colors.photoVeil))
        } else {
            Box(Modifier.matchParentSize().background(Brush.linearGradient(0f to colors.heroIndigo, 0.3f to colors.heroViolet, 1f to colors.heroSky)))
        }
        Column(Modifier.padding(horizontal = Tokens.Space5, vertical = Tokens.Space4)) {
            Caps("Today's session", "오늘의 공부")
            when {
                today == null -> Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                today.questions == 0 && today.cards == 0 ->
                    Text("Study Dumbass!! Obsession!!", style = MaterialTheme.typography.titleMedium, color = Color.White)
                else -> Row(Modifier.fillMaxWidth().padding(top = Tokens.Space1), horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
                    Stat("Questions", "문제", "${today.questions}", Modifier.weight(1f))
                    Stat("Accuracy", "정확도", percent(today.accuracy), Modifier.weight(1f))
                    Stat("Cards", "카드", "${today.cards}", Modifier.weight(1f))
                    Stat("Time", "시간", studyTime(today.studySeconds), Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(Tokens.Space3))
            FixThisNext(recommended, onPractice, onAsk)
            if (notice != null) Text(notice, Modifier.padding(top = Tokens.Space2), style = MaterialTheme.typography.bodySmall, color = colors.onHeroSoft)
        }
    }
}

@Composable
private fun Caps(text: String, ko: String) {
    val color = JustStudyTheme.colors.onHeroSoft
    Row(Modifier.semantics { heading() }, verticalAlignment = Alignment.Bottom) {
        Text(text.uppercase(), Modifier.alignByBaseline(), style = MaterialTheme.typography.labelMedium, color = color)
        KoSide(ko, MaterialTheme.typography.labelMedium.fontSize, Modifier.alignByBaseline(), color = color)
    }
}

@Composable
private fun Stat(label: String, ko: String, value: String, modifier: Modifier) {
    val soft = JustStudyTheme.colors.onHeroSoft
    Column(modifier.clearAndSetSemantics { contentDescription = "$label: $value" }) {
        // At large font sizes the label shrinks (to 9 sp) instead of breaking "Questions" in two.
        BasicText(label, style = MaterialTheme.typography.bodySmall.copy(color = soft), maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.bodySmall.fontSize))
        Text(ko, style = JustStudyTheme.korean.koBelow, color = soft, maxLines = 1)
        BasicText(value, style = MaterialTheme.typography.headlineSmall.copy(color = Color.White), maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = MaterialTheme.typography.headlineSmall.fontSize))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FixThisNext(recommended: List<Recommendation>?, onPractice: (String) -> Unit, onAsk: () -> Unit) {
    val colors = JustStudyTheme.colors
    Caps("Fix this next", "다음에 고칠 것")
    val top = recommended?.firstOrNull()
    if (recommended == null) return
    if (top == null) {
        Text("Answer some quiz questions first. I can't read your mind.", style = MaterialTheme.typography.bodyMedium, color = colors.onHeroSoft)
        return
    }
    FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(Tokens.Space2), itemVerticalAlignment = Alignment.CenterVertically) {
        Text(shownTopic(top.name), style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = Color.White)
        val (symbol, label) = when (top.priority) { "high" -> "!" to "High priority"; "medium" -> "~" to "Medium priority"; else -> "✓" to "Low priority" }
        val (fill, ink) = when (top.priority) {
            "high" -> colors.priorityHighOnPhoto to colors.wrong
            "medium" -> colors.priorityMediumOnPhoto to colors.fillWarn
            else -> colors.priorityLowOnPhoto to colors.correct
        }
        Text(
            "$symbol ${label.uppercase()}",
            Modifier.background(fill, RoundedCornerShape(Tokens.RadiusMd)).padding(horizontal = Tokens.Space2, vertical = 2.dp)
                .clearAndSetSemantics { contentDescription = label },
            style = MaterialTheme.typography.labelMedium, color = ink,
        )
    }
    val also = recommended.drop(1).joinToString(", ") { shownTopic(it.name) }
    Text(
        buildAnnotatedString {
            append(top.reasons.joinToString(" · "))
            if (also.isNotEmpty()) {
                pushStyle(SpanStyle(fontSize = 12.sp))
                append(" · Also: $also")
                pop()
            }
        },
        style = MaterialTheme.typography.bodyMedium, color = colors.onHeroSoft,
    )
    Spacer(Modifier.height(Tokens.Space2))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
        StickerButton("Practice this", { onPractice(top.name) }, ko = "연습하기", color = StickerColor.Go, size = StickerSize.Mini)
        Text("아니면", Modifier.clearAndSetSemantics { }, style = JustStudyTheme.korean.koBody.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = colors.onHeroSoft)
        StickerButton("Ask what to do", onAsk, ko = "뭘 할지 묻기", color = StickerColor.Action, size = StickerSize.Mini)
    }
}
