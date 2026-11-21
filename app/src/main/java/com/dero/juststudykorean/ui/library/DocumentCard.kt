package com.dero.juststudykorean.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import com.dero.juststudykorean.ui.components.PillTone
import com.dero.juststudykorean.ui.components.Pill
import com.dero.juststudykorean.data.ai.MessyNotesState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.data.documents.LibraryDocument
import com.dero.juststudykorean.data.documents.STAGE_CHUNKING
import com.dero.juststudykorean.data.documents.STAGE_EXTRACTING
import com.dero.juststudykorean.data.documents.STAGE_INDEXING
import com.dero.juststudykorean.data.documents.STAGE_PARSING
import com.dero.juststudykorean.data.documents.STAGE_PENDING
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.components.StickerSize
import com.dero.juststudykorean.ui.components.plural
import com.dero.juststudykorean.ui.components.FileName
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val uploadedFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/** What the card shows: Queued, Processing… (with its stage), Ready, Failed or Not supported. */
internal enum class DocState(val label: String) { Queued("Queued"), Processing("Processing…"), Ready("Ready"), Failed("Failed"), NotSupported("Not supported") }

internal fun stateOf(d: LibraryDocument): DocState = when {
    d.stage == STAGE_PENDING || (d.stage == null && d.document.status == "uploaded") -> DocState.Queued
    d.stage != null || d.document.status == "processing" -> DocState.Processing
    d.document.status == "ready" -> DocState.Ready
    d.document.status == "unsupported_scanned" -> DocState.NotSupported
    else -> DocState.Failed
}

private val STAGES = listOf(STAGE_EXTRACTING to "Extracting text", STAGE_PARSING to "Parsing", STAGE_CHUNKING to "Chunking", STAGE_INDEXING to "Indexing")

/** A document (design/library.md A): name, date, state pill, Reprocess, progress or counts, warnings and actions. */
@Composable
internal fun DocumentCard(
    item: LibraryDocument,
    open: Boolean,
    extracted: ExtractedState?,
    zone: ZoneId,
    onReprocess: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    messy: MessyNotesState? = null,
    onAddPairs: (List<com.dero.juststudykorean.korean.VocabPair>) -> Unit = {},
) {
    val c = JustStudyTheme.colors
    val doc = item.document
    val state = stateOf(item)
    Column(
        modifier.fillMaxWidth()
            .border(Tokens.Border, c.line, MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .padding(Tokens.Space4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FileName(doc.originalName, style = JustStudyTheme.korean.bodyBold)
                if (state != DocState.Ready) Text("Uploaded ${uploadedFormat.format(Instant.ofEpochMilli(doc.createdAt).atZone(zone))}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = c.muted)
            }
            Spacer(Modifier.width(Tokens.Space2))
            StatePill(state)
            var trash by remember { mutableIntStateOf(0) }
            IconButton({ trash++; onDelete() }, Modifier.size(48.dp)) {
                LucideIcon(Lucide.Trash2, contentDescription = "Delete", size = 20.dp, tint = c.wrongInk, animateKey = trash)
            }
        }
        when (state) {
            DocState.Processing -> {
                val step = STAGES.indexOfFirst { it.first == item.stage }.coerceAtLeast(0)
                LinearProgressIndicator(progress = { (step + 1f) / STAGES.size }, Modifier.fillMaxWidth(), color = c.brand, trackColor = c.tint)
                Text(STAGES[step].second, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = c.muted)
            }
            DocState.Queued -> Unit
            DocState.Ready -> {
                Text(
                    listOfNotNull(
                        "Uploaded ${uploadedFormat.format(Instant.ofEpochMilli(doc.createdAt).atZone(zone))}", plural(doc.pageCount.toInt(), "page"),
                        plural(doc.tableCount.toInt(), "table").takeIf { doc.tableCount > 0 }, plural(doc.charCount.toInt(), "character"),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = c.muted,
                )
                // design/library.md E: the messy notes wait for the app.
                if (messy?.waiting == true) Text("Waiting for you to open the app to read messy notes.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = c.muted)
            }
            DocState.Failed, DocState.NotSupported -> doc.errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = c.wrongInk)
            }
        }
        if (item.warnings.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space2), verticalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
                item.warnings.forEach { Pill(it, PillTone.Warn) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
            StickerButton("Details", onDetails, ko = "자세히", color = StickerColor.Gray, size = StickerSize.Small, enabled = state == DocState.Ready)
            Spacer(Modifier.weight(1f))
            var again by remember { mutableIntStateOf(0) }
            LabelledIcon(Lucide.RefreshCw, "Reprocess", again, enabled = state != DocState.Processing && state != DocState.Queued) { again++; onReprocess() }
            var taps by remember { mutableIntStateOf(0) }
            LabelledIcon(Lucide.Download, "Save PDF", taps, enabled = true) { taps++; onSave() }
        }
        if (open) ExtractedView(extracted, aiPairs = messy?.pairs.orEmpty(), onAdd = onAddPairs)
    }
}

@Composable
private fun StatePill(state: DocState) {
    val tone = when (state) {
        DocState.Queued -> PillTone.Neutral
        DocState.Processing -> PillTone.Info
        DocState.Ready -> PillTone.Correct
        DocState.Failed -> PillTone.Wrong
        DocState.NotSupported -> PillTone.Warn
    }
    Pill(state.label, tone)
}

/** P-22: a 20 dp `control` icon with its visible label (12 sp, 700, `muted`), 48 dp tall. */
@Composable
private fun LabelledIcon(icon: Lucide, label: String, animateKey: Int, enabled: Boolean, onClick: () -> Unit) {
    val c = JustStudyTheme.colors
    Row(
        Modifier.heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = Tokens.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(icon, contentDescription = null, size = 20.dp, tint = if (enabled) c.control else c.muted, animateKey = animateKey)
        Spacer(Modifier.width(Tokens.Space1))
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = c.muted)
    }
}
