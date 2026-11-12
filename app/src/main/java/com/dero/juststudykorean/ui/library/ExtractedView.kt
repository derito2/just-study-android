package com.dero.juststudykorean.ui.library

import com.dero.juststudykorean.ui.components.StickerSize
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.AiMark
import com.dero.juststudykorean.korean.VocabPair
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.ui.components.SelectedGroup
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/** What the app extracted (design/library.md B): Words, Grammar and Fragments, as the quiz, cards and teacher use them. */
@Composable
internal fun ExtractedView(state: ExtractedState?, modifier: Modifier = Modifier, aiPairs: List<VocabPair> = emptyList(), onAdd: (List<VocabPair>) -> Unit = {}) {
    val c = JustStudyTheme.colors
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
    Column(modifier.fillMaxWidth().padding(top = Tokens.Space2), verticalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
        val e = (state as? ExtractedState.Loaded)?.extracted
        when {
            state == null || state is ExtractedState.Loading -> { Text("Loading what the app read…", style = small, color = c.muted); return@Column }
            state is ExtractedState.Failed || e == null -> { Text("Could not read what the app extracted.", style = small, color = c.wrong); return@Column }
        }
        e!!
        var tab by rememberSaveable { mutableIntStateOf(0) }
        // The three tabs share the width on one line each: the name, its count below; the sentence under them says what they are.
        val mine = e.words.map { it.korean }.toSet()
        val ai = aiPairs.filter { it.korean !in mine } // use 6: the model's pairs of your messy notes (never cards on their own)
        SelectedGroup(listOf("Words", "Grammar", "Fragments"), tab, { tab = it }, ko = listOf(e.words.size + ai.size, e.grammar.size, e.fragments.size).map(Int::toString))
        val ink = MaterialTheme.colorScheme.onSurface
        when (tab) {
            0 -> {
                Text("Words detected (${e.words.size + ai.size}), as the quiz and flashcards use them", style = small, color = c.muted)
                // design/library.md E (phase 7): the model's pairs first; they are yours only once you add them.
                if (ai.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
                        Text("Found by AI in messy notes", Modifier.weight(1f), style = JustStudyTheme.korean.bodyBold.copy(fontSize = 14.sp), color = ink)
                        AiMark(short = true)
                    }
                    StickerButton("Add all", { onAdd(ai) }, color = StickerColor.Action, size = StickerSize.Small)
                    ai.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.korean, Modifier.weight(1f), style = JustStudyTheme.korean.koBody.copy(fontWeight = FontWeight.Bold), color = ink)
                            Text(p.english, Modifier.weight(1f).padding(end = Tokens.Space2), style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.End)
                            StickerButton("Add", { onAdd(listOf(p)) }, color = StickerColor.Gray, size = StickerSize.Small)
                        }
                    }
                }
                e.words.forEach { w ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(w.korean, Modifier.weight(1f), style = JustStudyTheme.korean.koBody.copy(fontWeight = FontWeight.Bold), color = ink)
                        Spacer(Modifier.width(Tokens.Space2))
                        Text(w.english, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.End)
                    }
                }
            }
            1 -> {
                Text("Grammar detected (${e.grammar.size})", style = small, color = c.muted)
                e.grammar.forEach { g ->
                Column(Modifier.fillMaxWidth().background(c.canvas, MaterialTheme.shapes.medium).padding(Tokens.Space3)) {
                    Text(if (g.kind == "particle") "Particle" else "Pattern", style = small, color = c.muted)
                    Text(g.pattern, style = JustStudyTheme.korean.koBody.copy(fontWeight = FontWeight.Bold), color = ink)
                    if (g.meaning.isNotEmpty()) Text("Meaning: ${g.meaning}", style = MaterialTheme.typography.bodyMedium, color = ink)
                    g.examples.firstOrNull()?.let { (ko, en) ->
                        Text(ko, style = JustStudyTheme.korean.koBody, color = ink)
                        if (en.isNotEmpty()) Text(en, style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    }
                }
                }
            }
            else -> if (e.fragments.isEmpty()) {
                Text("No fragments: your teacher has nothing to read here.", style = small, color = c.muted)
            } else {
                Text("Fragments (${e.fragments.size}), as your teacher reads them", style = small, color = c.muted)
                e.fragments.forEach { f ->
                    Column(Modifier.fillMaxWidth().background(c.canvas, MaterialTheme.shapes.medium).padding(Tokens.Space3)) {
                        Text("${f.pageNumber} 쪽 · ${f.contentType}", style = small, color = c.muted)
                        Text(f.text, style = JustStudyTheme.korean.koBody, color = ink)
                    }
                }
            }
        }
    }
}
