package com.dero.juststudykorean.ui.components

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.korean.Comparison
import com.dero.juststudykorean.korean.Mark
import com.dero.juststudykorean.korean.Verdict
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/** "1 wrong letter: 간 → 관": the letters that differ when both have the same length, else the whole answers. */
fun typedSummary(typed: String, c: Comparison): String? {
    if (c.verdict != Verdict.Almost && c.verdict != Verdict.Wrong) return null
    val a = typed.trim().codePoints().toArray().map { String(Character.toChars(it)) }
    val b = c.closest.codePoints().toArray().map { String(Character.toChars(it)) }
    val wrong = c.letters.count { it.mark == Mark.Wrong }
    val (from, to) = if (a.size == b.size) a.indices.filter { a[it] != b[it] }.let { d -> d.joinToString("") { a[it] } to d.joinToString("") { b[it] } } else typed.trim() to c.closest
    val n = if (a.size == b.size) wrong else c.off
    return "$n ${if (n == 1) "wrong letter" else "wrong letters"}: $from → $to"
}

/** Type the answer, on the back (design/flashcards.md C): the verdict, your answer letter by letter, and a summary. */
@Composable
fun TypedResult(typed: String, c: Comparison, modifier: Modifier = Modifier) {
    val colors = JustStudyTheme.colors
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(c) { // R-010: once, when the result shows
        haptic.performHapticFeedback(if (c.verdict == Verdict.Exact || c.verdict == Verdict.Meaning) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
    }
    val (text, color, icon) = when (c.verdict) {
        Verdict.Exact -> Triple("Exactly right", colors.correct, Lucide.Check)
        Verdict.Meaning -> Triple("Right meaning, different words", colors.correct, Lucide.Check)
        Verdict.Almost -> Triple("Almost", colors.fillWarn, null)
        Verdict.Wrong -> Triple("Not quite", colors.wrong, Lucide.X)
        Verdict.Empty -> Triple("You didn't type an answer", colors.muted, null)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
            if (icon != null) LucideIcon(icon, contentDescription = null, size = 18.dp, tint = color)
            Text(text, style = MaterialTheme.typography.titleMedium, color = color)
        }
        if (c.letters.isNotEmpty()) {
            val word = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = colors.muted)) { append("Your answer: ") }
                c.letters.forEach { l ->
                    val style = when (l.mark) {
                        Mark.Ok -> SpanStyle(color = colors.correct)
                        Mark.Wrong -> SpanStyle(color = colors.wrong, background = colors.wrongFill, textDecoration = TextDecoration.Underline)
                        Mark.Extra -> SpanStyle(color = colors.wrong, background = colors.wrongFill, textDecoration = TextDecoration.LineThrough)
                        Mark.Missing -> SpanStyle(color = colors.muted, textDecoration = TextDecoration.Underline)
                    }
                    withStyle(style) { append(if (l.char == " ") " " else l.char) }
                }
            }
            val spoken = c.letters.filter { it.mark != Mark.Ok }.joinToString(", ") { "${it.mark.name.lowercase()} ${it.char}" }
            Text(word, Modifier.semantics { if (spoken.isNotEmpty()) contentDescription = "Your answer: ${c.letters.joinToString("") { it.char }}. $spoken" },
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        }
        typedSummary(typed, c)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.muted, textAlign = TextAlign.Center) }
    }
}
