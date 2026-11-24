package com.dero.juststudykorean.ui.flashcards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.korean.flashcards.Cloze
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.Pill
import com.dero.juststudykorean.ui.components.PillTone
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

// F2, design/beta.md 2.2–2.4: the faces of Fill the Gap and Listen Only, and the small 🔊 of every card.

@Composable
private fun Modifier.face() = this
    .fillMaxWidth()
    .heightIn(min = 256.dp)
    .border(Tokens.Border, JustStudyTheme.colors.line, MaterialTheme.shapes.large)
    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
    .padding(Tokens.Space6)

private val sentenceStyle @Composable get() = JustStudyTheme.korean.koTitle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold)

/** 🔊 on the face with the Korean: 40 dp (`action`, volume-2 of 20 dp), in a 48 dp touch area. */
@Composable
fun SpeakerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "Hear it" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).background(JustStudyTheme.colors.action, CircleShape), contentAlignment = Alignment.Center) {
            LucideIcon(Lucide.Volume2, contentDescription = null, size = 20.dp, tint = Color.White)
        }
    }
}

/** A face with 🔊 at its top right when [speak] is not null (only with the voice ready). */
@Composable
fun WithSpeaker(speak: (() -> Unit)?, modifier: Modifier, face: @Composable (Modifier) -> Unit) {
    Box(modifier) {
        face(Modifier.fillMaxSize())
        if (speak != null) SpeakerButton(speak, Modifier.align(Alignment.TopEnd).padding(Tokens.Space1))
    }
}

/** 2.2 front: FILL THE GAP, your sentence with a 4-character gap, its translation and, with Type the answer, the field. */
@Composable
fun GapFront(cloze: Cloze, modifier: Modifier = Modifier, typingField: (@Composable () -> Unit)? = null) {
    val colors = JustStudyTheme.colors
    val line = colors.line
    val text = buildAnnotatedString {
        append(cloze.before)
        appendInlineContent("gap", "____")
        append(cloze.after)
    }
    val gap = InlineTextContent(Placeholder(4.em, 1.2.em, PlaceholderVerticalAlign.TextBottom)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { Box(Modifier.fillMaxWidth().height(2.dp).background(line)) }
    }
    Column(
        modifier.face().semantics { contentDescription = "Card front, fill the gap" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.Space4, Alignment.CenterVertically),
    ) {
        Pill("FILL THE GAP", PillTone.Info)
        Text(text, style = sentenceStyle, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, inlineContent = mapOf("gap" to gap))
        Text(cloze.translation, fontSize = 14.sp, color = colors.muted, textAlign = TextAlign.Center)
        if (typingField != null) Box(Modifier.widthIn(max = 320.dp)) { typingField() }
    }
}

/** 2.2 back: the whole sentence, the word in `brand-text` 700, and 🔊 of the sentence. Goes under the word on the back. */
@Composable
fun GapSentence(cloze: Cloze, speak: (() -> Unit)?) {
    val text = buildAnnotatedString {
        append(cloze.before)
        withStyle(SpanStyle(color = JustStudyTheme.colors.brandText, fontWeight = FontWeight.Bold)) { append(cloze.answer) }
        append(cloze.after)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f, fill = false), style = sentenceStyle.copy(fontWeight = FontWeight.Normal), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        if (speak != null) SpeakerButton(speak)
    }
}

/** 2.3 front: LISTEN, a 72 dp round button (it sounds when the card comes up, and again on tap); no Korean text. */
@Composable
fun ListenFront(onSpeak: () -> Unit, modifier: Modifier = Modifier, typingField: (@Composable () -> Unit)? = null) {
    Column(
        modifier.face().semantics { contentDescription = "Card front, listen" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.Space4, Alignment.CenterVertically),
    ) {
        Pill("LISTEN", PillTone.Info)
        Box(
            Modifier.size(72.dp).background(JustStudyTheme.colors.action, CircleShape).clickable(role = Role.Button, onClick = onSpeak)
                .semantics { contentDescription = "Hear it again" },
            contentAlignment = Alignment.Center,
        ) { LucideIcon(Lucide.Volume2, contentDescription = null, size = 32.dp, tint = Color.White) }
        Text("Tap to hear it again.", style = MaterialTheme.typography.bodySmall, color = JustStudyTheme.colors.muted)
        if (typingField != null) Box(Modifier.widthIn(max = 320.dp)) { typingField() }
    }
}
