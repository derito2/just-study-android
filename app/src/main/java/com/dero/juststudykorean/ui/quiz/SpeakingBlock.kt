package com.dero.juststudykorean.ui.quiz

import com.dero.juststudykorean.ui.components.PillTone
import com.dero.juststudykorean.ui.components.Pill
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.korean.KOREAN_ALTERNATIVES
import com.dero.juststudykorean.ui.components.KoSide
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.components.StickerSize
import com.dero.juststudykorean.ui.components.FileName
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens
import com.dero.juststudykorean.ui.components.speech.SpeakActions
import com.dero.juststudykorean.ui.components.speech.SpeakPhase
import com.dero.juststudykorean.ui.components.speech.SpeakingUi
import com.dero.juststudykorean.ui.components.speech.SpeakingBlock
import com.dero.juststudykorean.ui.components.speech.Comparison
import com.dero.juststudykorean.ui.components.speech.TextAction

/** The English meaning of a write_ko question ("Write in Korean: \"Apple\"" → Apple). */
fun meaningOf(q: QuestionsEntity): String = Regex("^Write in Korean: \"(.*)\"$").find(q.prompt)?.groupValues?.get(1) ?: q.prompt

/** design/quiz-speaking.md B: the card's top row, the question and the spoken block (instead of the answer field). */
@Composable
fun SpokenQuestion(q: QuestionsEntity, document: String?, speaking: SpeakingUi, actions: SpeakActions, voiceReady: Boolean, permissionNeeded: Boolean) {
    val c = JustStudyTheme.colors
    val ink = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The title and the chip take what they need; the document's name gets the rest and shortens (on the phone, with a bigger
        // font, "PRACTICE" used to break in two next to a name like "Anki_vocabulary_1").
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Say it in Korean", Modifier.alignByBaseline(), style = MaterialTheme.typography.bodySmall, color = c.muted)
            KoSide("한국어로 말하기", 12.sp, Modifier.alignByBaseline(), color = c.muted)
            Pill("PRACTICE", PillTone.Info, Modifier.padding(start = Tokens.Space2))
        }
        document?.let {
            FileName(
                it, Modifier.weight(1f).padding(start = Tokens.Space2), style = MaterialTheme.typography.bodySmall, color = c.muted,
                maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        } ?: Spacer(Modifier.weight(1f))
    }
    val answer = q.correctAnswer.split(KOREAN_ALTERNATIVES).first().trim()
    if (readAloud(q)) {
        Text(answer, style = JustStudyTheme.korean.koPrompt, color = ink)
        Text(meaningOf(q), style = MaterialTheme.typography.bodyMedium, color = c.muted)
        Text("Read it out loud.", style = MaterialTheme.typography.bodySmall, color = c.muted)
    } else {
        Text(meaningOf(q), style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = ink)
        Text("Say it out loud, in Korean.", style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
    SpeakingBlock(speaking, actions, permissionNeeded)
    val shown = speaking.comparison
    if (shown != null || speaking.shownAnswer) Comparison(shown, if (shown == null) q.correctAnswer else null, voiceReady, actions.onListen)
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space4)) {
        if (speaking.phase == SpeakPhase.Compared) TextAction("Try again", c.brandText, actions.onTryAgain)
        if (speaking.attempts == 0 && !speaking.shownAnswer && speaking.phase in setOf(SpeakPhase.Ready, SpeakPhase.NoSpeech)) StickerButton("Show answer", actions.onShowAnswer, ko = "답 보기", color = StickerColor.Gray, size = StickerSize.Small)
    }
}
