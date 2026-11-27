package com.dero.juststudykorean.ui.reader

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.reader.ReaderLine
import com.dero.juststudykorean.korean.reader.Gloss
import com.dero.juststudykorean.korean.reader.tokens
import com.dero.juststudykorean.ui.components.AiMark
import com.dero.juststudykorean.ui.components.EmptyState
import com.dero.juststudykorean.ui.components.FileName
import com.dero.juststudykorean.ui.components.JsSheet
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.LoadingState
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.Pill
import com.dero.juststudykorean.ui.components.PillTone
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.components.StickerSize
import com.dero.juststudykorean.ui.components.displayName
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalKoreanTypography
import com.dero.juststudykorean.ui.theme.Tokens
import kotlinx.coroutines.launch

private const val NOT_IN_NOTES = "Not in your notes."

@Composable
fun ReaderScreen(documentId: Long?, onBack: () -> Unit, vm: ReaderViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.open(documentId) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val back = { if (!vm.back()) onBack() }
    BackHandler(onBack = back)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    ReaderContent(
        ui, onBack = back, onChoose = { vm.choose(it) }, onTap = { vm.tap(it) }, onLongPress = vm::longPress,
        sheet = SheetActions(
            onClose = vm::closeSheet, onSpeak = vm::speakWord, onAdd = { vm.chooseDeck() }, onDeck = { vm.addTo(it) },
            onExplain = { vm.explain() }, onHear = vm::hearSentence,
            onCopy = { line -> scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("sentence", line))) }; vm.closeSheet() },
        ),
    )
}

class SheetActions(
    val onClose: () -> Unit, val onSpeak: () -> Unit, val onAdd: () -> Unit, val onDeck: (Long?) -> Unit,
    val onExplain: () -> Unit, val onHear: () -> Unit, val onCopy: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderContent(
    ui: ReaderUi, onBack: () -> Unit, onChoose: (DocumentsEntity) -> Unit, onTap: (String) -> Unit,
    onLongPress: (String) -> Unit, sheet: SheetActions,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pad = Modifier.padding(horizontal = Tokens.Space4)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().nestedScroll(scroll.nestedScrollConnection)) {
        val doc = ui.document
        JsTopBar("Reader", "읽기", doc?.let { displayName(it.originalName, 40) } ?: "Tap a word to see your notes.", null, scroll, onMore = null, onBack = onBack)
        when {
            ui.loading -> LoadingState(modifier = pad)
            doc == null && ui.documents.isEmpty() -> EmptyState(Lucide.BookOpen, "Nothing to read yet", null, "Your Ready documents show here.", pad)
            doc == null -> LazyColumn(Modifier.weight(1f)) { items(ui.documents, key = { it.id }) { DocumentRow(it) { onChoose(it) } } }
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(Tokens.Space4)) {
                items(ui.pages, key = { it.page }) { page ->
                    Column(Modifier.padding(bottom = Tokens.Space4)) {
                        Text("Page ${page.page}", style = MaterialTheme.typography.bodySmall, color = JustStudyTheme.colors.muted)
                        Box(Modifier.padding(top = Tokens.Space1, bottom = Tokens.Space2).fillMaxWidth().height(2.dp).background(JustStudyTheme.colors.line))
                        page.lines.forEach { Line(it, onTap, onLongPress) }
                    }
                }
            }
        }
    }
    when {
        ui.gloss != null -> JsSheet(sheet.onClose) { GlossSheet(ui, sheet) }
        ui.sentence != null -> JsSheet(sheet.onClose) { SentenceSheet(ui.sentence, sheet) }
    }
}

@Composable
private fun DocumentRow(doc: DocumentsEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Tokens.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(Lucide.FileText, contentDescription = null, size = 20.dp, tint = JustStudyTheme.colors.muted)
        Spacer(Modifier.width(Tokens.Space3))
        FileName(displayName(doc.originalName, 40), Modifier.weight(1f), maxLines = 1)
        Text("${doc.pageCount} ${if (doc.pageCount == 1L) "page" else "pages"}", style = MaterialTheme.typography.bodySmall, color = JustStudyTheme.colors.muted)
    }
}

/** One Text per line, each Korean word a link (never one Text per word); a long press opens the sentence's options. */
@Composable
private fun Line(line: ReaderLine, onTap: (String) -> Unit, onLongPress: (String) -> Unit) {
    val tint = JustStudyTheme.colors.tint
    val text = remember(line.text, tint) {
        buildAnnotatedString {
            append(line.text)
            val styles = TextLinkStyles(pressedStyle = SpanStyle(background = tint)) // the tapped word lights up a moment
            for (t in tokens(line.text)) addLink(LinkAnnotation.Clickable(t.text, styles) { onTap(t.text) }, t.start, t.end)
        }
    }
    val style = if (line.heading) MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    else LocalKoreanTypography.current.koBody.copy(fontSize = 16.sp, lineHeight = 24.sp, fontFamily = FontFamily.Default, color = MaterialTheme.colorScheme.onSurface)
    Text(
        text, style = style,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .pointerInput(line.text) { detectTapGestures(onLongPress = { onLongPress(line.text) }) }
            .semantics { onLongClick("Explain, hear or copy") { onLongPress(line.text); true } },
    )
}

@Composable
internal fun GlossSheet(ui: ReaderUi, sheet: SheetActions) {
    val gloss = ui.gloss ?: return
    val colors = JustStudyTheme.colors
    val ko = LocalKoreanTypography.current
    val entry = when (gloss) { is Gloss.Entry -> gloss; is Gloss.Inflected -> gloss.entry; else -> null }
    Column(Modifier.padding(horizontal = Tokens.Space6), verticalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
        if (ui.decks != null) {
            Text("Add to deck", style = MaterialTheme.typography.titleMedium)
            DeckRow("New deck", Lucide.Plus) { sheet.onDeck(null) }
            ui.decks.forEach { d -> DeckRow(d.name, Lucide.FileText) { sheet.onDeck(d.id) } }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
            Text(if (gloss is Gloss.Inflected) "${gloss.surface} → ${gloss.lemma}" else gloss.word(), style = ko.koTitle, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
            if (gloss is Gloss.Inflected) gloss.particle?.let { Pill(it, PillTone.Neutral) }
        }
        when {
            gloss is Gloss.NotFound -> {
                Text(NOT_IN_NOTES, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                gloss.sentences.forEach { Text(it, style = ko.koBody, color = MaterialTheme.colorScheme.onSurface) }
            }
            gloss.meaning() != null -> Text(gloss.meaning()!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            else -> Text(NOT_IN_NOTES, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        }
        entry?.examples?.forEach { (k, e) ->
            Column {
                Text(k, style = ko.koBody, color = MaterialTheme.colorScheme.onSurface)
                if (e.isNotEmpty()) Text(e, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
        if (entry != null && entry.source.isNotEmpty()) Text("From ${displayName(entry.source, 40)}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
        ui.added?.let { Pill(it, PillTone.Correct) }
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space3), verticalAlignment = Alignment.CenterVertically) {
            StickerButton("Listen", sheet.onSpeak, color = StickerColor.Gray, size = StickerSize.Small, icon = Lucide.Volume2)
            if (entry != null) StickerButton("Add to deck", sheet.onAdd, ko = "덱에 추가")
        }
    }
}

@Composable
private fun DeckRow(name: String, icon: Lucide, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        LucideIcon(icon, contentDescription = null, size = 20.dp, tint = JustStudyTheme.colors.muted)
        Spacer(Modifier.width(Tokens.Space3))
        FileName(name, Modifier.weight(1f), maxLines = 1)
    }
}

@Composable
internal fun SentenceSheet(s: SentenceUi, sheet: SheetActions) {
    val colors = JustStudyTheme.colors
    Column(Modifier.padding(horizontal = Tokens.Space6), verticalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
        Text(s.line, style = LocalKoreanTypography.current.koBody, color = MaterialTheme.colorScheme.onSurface)
        when {
            s.loading -> Text("Explaining…", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            s.explanation != null -> {
                AiMark()
                Text(s.explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            s.glosses != null -> s.glosses.forEach { (w, m) ->
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
                    Text(w, style = LocalKoreanTypography.current.koBody, color = MaterialTheme.colorScheme.onSurface)
                    Text(m, style = MaterialTheme.typography.bodyMedium, color = if (m == NOT_IN_NOTES) colors.muted else MaterialTheme.colorScheme.onSurface)
                }
            }
            else -> StickerButton("Explain this sentence", sheet.onExplain, modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
            StickerButton("Hear sentence", sheet.onHear, color = StickerColor.Gray, size = StickerSize.Small)
            StickerButton("Copy", { sheet.onCopy(s.line) }, color = StickerColor.Gray, size = StickerSize.Small)
        }
    }
}
