package com.dero.juststudykorean.ui.flashcards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.ui.components.JsDialog
import com.dero.juststudykorean.ui.components.JsTextField
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/** Combine into one deck (design/flashcards.md B): the two names, a required name, Cancel and Combine. */
@Composable
fun MergeDialog(names: List<String>, onCombine: (String) -> Unit, onDismiss: () -> Unit, initial: String = "") {
    var name by rememberSaveable { mutableStateOf(initial.ifEmpty { names.joinToString(" + ") }.take(120)) } // R1-10: a name is proposed
    JsDialog(
        "Combine into one deck", "합치기",
        "${names.joinToString(" + ")}. Your cards and their progress don't change.",
        onDismiss,
        confirm = { m -> StickerButton("Combine", { onCombine(name) }, m, ko = "합치기", enabled = name.isNotBlank()) },
        dismiss = { m -> StickerButton("Cancel", onDismiss, m, ko = "취소", color = StickerColor.Gray) },
    ) {
        Spacer(Modifier.height(Tokens.Space4))
        Column(Modifier.fillMaxWidth()) {
            Text("Name of the new deck", style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Tokens.Space2))
            JsTextField(name, { if (it.length <= 120) name = it }, Modifier.fillMaxWidth(), onDone = { if (name.isNotBlank()) onCombine(name) })
            if (name.isBlank()) Text("Give the new deck a name.", Modifier.padding(top = Tokens.Space1), fontSize = 12.sp, color = JustStudyTheme.colors.wrong)
        }
    }
}
