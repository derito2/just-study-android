package com.dero.juststudykorean.ui.library

import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dero.juststudykorean.data.ai.MessyNotesState
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.data.documents.LibraryDocument
import com.dero.juststudykorean.ui.components.ErrorState
import com.dero.juststudykorean.ui.components.JsCheckbox
import com.dero.juststudykorean.ui.components.JsDialog
import com.dero.juststudykorean.ui.components.JsSnackbarHost
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.LoadingState
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.components.StickerSize
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens
import java.time.ZoneId

@Composable
fun LibraryScreen(onMore: () -> Unit, onAllVocabulary: () -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val docs by vm.documents.collectAsStateWithLifecycle()
    val uploading by vm.uploading.collectAsStateWithLifecycle()
    val open by vm.open.collectAsStateWithLifecycle()
    val messy by vm.messy.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) { vm.readMessyNotes(); onPauseOrDispose {} }
    val extracted by vm.extracted.collectAsStateWithLifecycle()
    val duplicates by vm.duplicates.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.upload(it) }
    // The first upload asks for notifications (the progress); if you say no, the PDF is processed all the same.
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { pick.launch(arrayOf("application/pdf")) }
    var saving by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val stored = saving
        saving = null
        if (uri != null && stored != null) vm.savePdf(stored, uri)
    }

    LibraryContent(
        docs, uploading, open, extracted, duplicates, snackbar, ZoneId.systemDefault(), onMore, onAllVocabulary,
        onUpload = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                pick.launch(arrayOf("application/pdf"))
            } else {
                ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onReprocess = vm::reprocess, onDetails = vm::toggle, onDelete = vm::delete,
        onSave = { d -> saving = d.document.storedName; save.launch(d.document.originalName) },
        onRetry = vm::retry, onSkip = vm::skipDuplicate, onSkipAll = vm::skipAllDuplicates, messy = messy,
    )
}

/** Library (design/library.md): upload, All vocabulary and a Card per PDF, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    state: DocumentsState,
    uploading: Boolean,
    open: Long?,
    extracted: Map<Long, ExtractedState>,
    duplicates: DuplicatesShown?,
    snackbar: SnackbarHostState,
    zone: ZoneId,
    onMore: () -> Unit,
    onAllVocabulary: () -> Unit,
    onUpload: () -> Unit,
    onReprocess: (Long) -> Unit,
    onDetails: (Long) -> Unit,
    onDelete: (Long, Boolean) -> Unit,
    onSave: (LibraryDocument) -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    onSkipAll: () -> Unit,
    deleteAtStart: LibraryDocument? = null,
    messy: Map<Long, MessyNotesState> = emptyMap(), // use 6 (phase 6)
) {
    val c = JustStudyTheme.colors
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var deleting by remember { mutableStateOf(deleteAtStart) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
            JsTopBar("Library", "자료실", "Your study PDFs. No notes, no progress.", "공부용 PDF. 노트가 없으면 발전도 없어.", scroll, onMore = onMore, onBack = null)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Tokens.Space4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
            ) {
                item(key = "upload") { UploadCard(uploading, onUpload) }
                item(key = "vocabulary") {
                    StickerButton("All vocabulary", onAllVocabulary, Modifier.fillMaxWidth(), ko = "모든 단어", color = StickerColor.Fix)
                }
                val docs = state.documents
                when {
                    state.error -> item(key = "error") { ErrorState("Could not load documents.", onRetry) }
                    docs == null -> item(key = "loading") { LoadingState("Loading…") }
                    docs.isEmpty() -> item(key = "empty") {
                        Text("No documents yet. Upload a PDF and get to work.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = c.muted)
                    }
                    else -> items(docs, key = { it.document.id }) { d ->
                        DocumentCard(
                            d, open == d.document.id, extracted[d.document.id], zone,
                            onReprocess = { onReprocess(d.document.id) }, onDetails = { onDetails(d.document.id) },
                            onDelete = { deleting = d }, onSave = { onSave(d) }, messy = messy[d.document.id],
                        )
                    }
                }
            }
        }
        JsSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    deleting?.let { d ->
        var withCards by rememberSaveable { mutableStateOf(false) }
        JsDialog(
            null, null, "Delete this document and its extracted text?", { deleting = null },
            confirm = { m -> StickerButton("Delete", { deleting = null; onDelete(d.document.id, withCards) }, m, ko = "삭제", color = StickerColor.Danger) },
            dismiss = { m -> StickerButton("Cancel", { deleting = null }, m, ko = "취소", color = StickerColor.Gray) },
        ) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { withCards = !withCards }.padding(vertical = Tokens.Space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JsCheckbox(withCards)
                Spacer(Modifier.width(Tokens.Space3))
                Text("Also delete its flashcards and their review history", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    duplicates?.let { DuplicateDialog(it, onSkip, onSkipAll) }
}

/** Upload a PDF: dashed 2 dp `field` border, 24 dp padding, centered; Choose files opens SAF (several PDFs at once). */
@Composable
private fun UploadCard(uploading: Boolean, onUpload: () -> Unit) {
    val c = JustStudyTheme.colors
    Column(
        Modifier.fillMaxWidth()
            .drawBehind {
                val r = 16.dp.toPx()
                drawRoundRect(
                    c.field, cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .padding(Tokens.Space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.Space2),
    ) {
        Text("Upload a PDF · PDF 올리기", style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(
            "Digital PDFs only, one or several at once. Scanned PDFs are not supported (no OCR).",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = c.muted, textAlign = TextAlign.Center,
        )
        StickerButton(if (uploading) "Uploading…" else "Choose files", onUpload, ko = if (uploading) "올리는 중…" else "파일 선택", enabled = !uploading, size = StickerSize.Default)
    }
}
