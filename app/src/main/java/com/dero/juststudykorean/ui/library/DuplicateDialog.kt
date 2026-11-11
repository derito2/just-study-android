package com.dero.juststudykorean.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.data.documents.DuplicateInfo
import com.dero.juststudykorean.ui.components.JsDialog
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/** design/library.md D: one dialog per repeated word or grammar point, "(n of m)" when there are several. */
@Composable
internal fun DuplicateDialog(shown: DuplicatesShown, onSkip: () -> Unit, onSkipAll: () -> Unit) {
    val d = shown.items[shown.index]
    val c = JustStudyTheme.colors
    val count = if (shown.items.size > 1) " (${shown.index + 1} of ${shown.items.size})" else ""
    val message = if (d.kind == "word") {
        "${shown.filename} repeats a word you already study. It stays as one word: its meanings and examples are combined (one flashcard)."
    } else {
        "${shown.filename} repeats a grammar point you already study. Your first version stays; the repeated one from this PDF is not saved again."
    }
    JsDialog(
        "Already in the app$count", "이미 있어요", message, onSkipAll,
        confirm = { m -> StickerButton("Skip", onSkip, m, ko = "건너뛰기", color = StickerColor.Action) },
        dismiss = { m -> StickerButton("Skip all", onSkipAll, m, ko = "모두 건너뛰기", color = StickerColor.Gray) },
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = Tokens.Space3), verticalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
            Text(d.text, style = JustStudyTheme.korean.koTitle, color = MaterialTheme.colorScheme.onSurface)
            Box("Now in the app", d.current.first(), Modifier.background(c.canvas, MaterialTheme.shapes.medium))
            Box("In the new PDF", d.new, Modifier.background(c.tint, MaterialTheme.shapes.medium))
            d.combined?.let { combined ->
                Column(Modifier.fillMaxWidth().border(Tokens.Border, c.line, MaterialTheme.shapes.medium).padding(Tokens.Space3)) {
                    Text("Combined", style = MaterialTheme.typography.labelMedium, color = c.muted)
                    Text(combined, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun Box(title: String, info: DuplicateInfo, modifier: Modifier) {
    val c = JustStudyTheme.colors
    val ink = MaterialTheme.colorScheme.onSurface
    Column(modifier.fillMaxWidth().padding(Tokens.Space3)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = c.muted)
        Text(info.meaning, style = MaterialTheme.typography.bodyMedium, color = ink)
        if (info.example.isNotEmpty()) Text(info.example, style = JustStudyTheme.korean.koBody, color = ink)
        if (info.exampleTranslation.isNotEmpty()) Text(info.exampleTranslation, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Text("${info.filename} · p. ${info.pageNumber}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = c.muted)
    }
}
