package com.dero.juststudykorean.ui.library

import com.dero.juststudykorean.korean.VocabPair
import com.dero.juststudykorean.data.ai.MessyNotesState
import com.dero.juststudykorean.data.ai.MessyNotes
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.documents.DocumentRepository
import com.dero.juststudykorean.data.documents.Duplicate
import com.dero.juststudykorean.data.documents.Extracted
import com.dero.juststudykorean.data.documents.LibraryDocument
import com.dero.juststudykorean.data.documents.VocabularyWord
import com.dero.juststudykorean.data.processing.ProcessingQueue
import com.dero.juststudykorean.data.processing.UploadResult
import com.dero.juststudykorean.processing.ProcessingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.dero.juststudykorean.ui.components.runCatchingCancellable
import kotlinx.coroutines.flow.flowOn
import com.dero.juststudykorean.data.documents.filterVocabulary

/** The documents list: null while loading; [error] if it could not be read. */
data class DocumentsState(val documents: List<LibraryDocument>? = null, val error: Boolean = false)

/** What the app extracted, per open card: loading, loaded, or nothing to show. */
sealed interface ExtractedState {
    data object Loading : ExtractedState
    data class Loaded(val extracted: Extracted?) : ExtractedState
    data object Failed : ExtractedState
}

/** The repeated words and grammar of a document just processed, shown one at a time. */
data class DuplicatesShown(val documentId: Long, val filename: String, val items: List<Duplicate>, val index: Int = 0)

internal fun skipKey(documentId: Long, d: Duplicate) = "$documentId:${d.kind}:${com.dero.juststudykorean.korean.norm(d.text)}"

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repo: DocumentRepository,
    private val queue: ProcessingQueue,
    private val files: DocumentFiles,
    private val messyNotes: MessyNotes? = null,
    private val prefs: com.dero.juststudykorean.data.prefs.UserPrefs? = null,
) : ViewModel() {
    /** Use 6: the model's pairs of each document's messy notes, and whether they wait for the app (design/library.md E). */
    val messy: StateFlow<Map<Long, MessyNotesState>> = messyNotes?.state ?: MutableStateFlow(emptyMap())

    /** On every return to the Library (the app in front): goes on with the messy notes not read yet. */
    /** The last pairs added (for Undo): document and pairs. */
    private var lastAdded: Pair<Long, List<VocabPair>>? = null
    private val added = Channel<Int>(Channel.BUFFERED)
    /** "Added {n} words." with Undo (design/library.md E). */
    val addedWords = added.receiveAsFlow()

    /** Add / Add all: the model's pairs become normal words of the document. */
    fun addPairs(documentId: Long, pairs: List<VocabPair>) {
        val m = messyNotes ?: return
        viewModelScope.launch {
            val done = runCatchingCancellable { m.accept(documentId, pairs) }.getOrDefault(emptyList())
            if (done.isEmpty()) return@launch
            lastAdded = documentId to done
            reload(documentId) // read again with the new words
            added.send(done.size)
        }
    }

    fun undoPairs() {
        val (id, pairs) = lastAdded ?: return
        lastAdded = null
        messyNotes?.let { m -> viewModelScope.launch { runCatchingCancellable { m.undo(id, pairs) }; reload(id) } }
    }

    fun readMessyNotes() { messyNotes?.let { m -> viewModelScope.launch { runCatchingCancellable { m.readReady() } } } }

    private val retry = MutableStateFlow(0)
    /** Documents uploaded or reprocessed here: when one is ready, its repeats are shown (DuplicateDialog). */
    private val watching = MutableStateFlow(emptySet<Long>())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val documents: StateFlow<DocumentsState> = retry.flatMapLatest {
        repo.documents().map { DocumentsState(it) }.catch { emit(DocumentsState(error = true)) }
    }
        .onEach { s -> s.documents?.let(::checkFinished) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentsState())

    private val _uploading = MutableStateFlow(false)
    val uploading = _uploading.asStateFlow()
    private val _open = MutableStateFlow<Long?>(null)
    val open = _open.asStateFlow()
    private val _extracted = MutableStateFlow<Map<Long, ExtractedState>>(emptyMap())
    val extracted = _extracted.asStateFlow()
    private val _duplicates = MutableStateFlow<DuplicatesShown?>(null)
    val duplicates = _duplicates.asStateFlow()

    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    fun retry() { retry.value++ }

    /** Copies each chosen PDF (SAF) to private storage and processes them, one at a time. */
    fun upload(uris: List<Uri>) {
        if (uris.isEmpty() || _uploading.value) return
        _uploading.value = true
        viewModelScope.launch {
            try {
                for (uri in uris) {
                    val name = withContext(Dispatchers.IO) {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        }
                    }
                    val result = runCatchingCancellable {
                        withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { queue.upload(it, name) } }
                    }.getOrElse { UploadResult.Rejected("Could not read that file.") }
                    when (result) {
                        is UploadResult.Queued -> watching.update { it + result.documentId }
                        is UploadResult.Rejected -> events.send(result.message)
                    }
                }
                if (queue.hasWork()) ProcessingService.start(context)
            } finally {
                _uploading.value = false
            }
        }
    }

    fun reprocess(id: Long) = viewModelScope.launch {
        runCatchingCancellable { repo.reprocess(id) }.onFailure { events.send(it.message ?: "Could not reprocess.") }.onSuccess {
            watching.update { it + id }
            _extracted.update { it - id }
            ProcessingService.start(context)
        }
    }

    fun delete(id: Long, withCards: Boolean) = viewModelScope.launch {
        runCatchingCancellable { repo.delete(id, withCards) }.onFailure { events.send("Could not delete the document.") }
        _extracted.update { it - id }
        if (_open.value == id) _open.value = null
    }

    /** Save PDF: a copy of the stored file where you choose (CreateDocument). */
    fun savePdf(storedName: String, uri: Uri) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            runCatchingCancellable {
                files.pdf(storedName).inputStream().use { input -> context.contentResolver.openOutputStream(uri, "wt")!!.use { input.copyTo(it) } }
            }.isSuccess
        }
        events.send(if (ok) "PDF saved." else "Couldn't save the PDF.")
    }

    /** Details: opens (and loads) what the app extracted; one card open at a time. */
    fun toggle(id: Long) {
        if (_open.value == id) { _open.value = null; return }
        _open.value = id
        if (_extracted.value[id] is ExtractedState.Loaded) return
        reload(id)
    }

    private fun reload(id: Long) {
        _extracted.update { it + (id to ExtractedState.Loading) }
        viewModelScope.launch {
            val state = runCatchingCancellable { ExtractedState.Loaded(repo.extracted(id)) }.getOrElse { ExtractedState.Failed }
            _extracted.update { it + (id to state) }
        }
    }

    private fun checkFinished(docs: List<LibraryDocument>) {
        for (d in docs) {
            val id = d.document.id
            if (id !in watching.value || d.stage != null || d.document.status in setOf("uploaded", "processing")) continue
            watching.update { it - id }
            if (d.document.status != "ready") continue
            viewModelScope.launch {
                val skipped = prefs?.skippedDuplicates?.first().orEmpty()
                val items = runCatchingCancellable { repo.duplicates(id) }.getOrNull().orEmpty().filter { skipKey(id, it) !in skipped }
                if (items.isNotEmpty() && _duplicates.value == null) _duplicates.value = DuplicatesShown(id, d.document.originalName, items)
            }
        }
    }

    fun skipDuplicate() {
        val s = _duplicates.value ?: return
        remember(s, s.items.subList(s.index, s.index + 1))
        _duplicates.value = s.takeIf { it.index + 1 < it.items.size }?.copy(index = s.index + 1)
    }

    fun skipAllDuplicates() {
        _duplicates.value?.let { s -> remember(s, s.items.drop(s.index)) }
        _duplicates.value = null
    }

    /** R1-14: what you skipped is not asked again when this document is reprocessed. */
    private fun remember(s: DuplicatesShown, items: List<com.dero.juststudykorean.data.documents.Duplicate>) {
        prefs?.let { p -> viewModelScope.launch { p.addSkippedDuplicates(items.map { skipKey(s.documentId, it) }) } }
    }
}

/** All vocabulary: the words of your ready documents, filtered while you type. */
@HiltViewModel
class AllVocabularyViewModel @Inject constructor(private val repo: DocumentRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val retry = MutableStateFlow(0)

    /** null while loading; failure is an empty list with [failed]. */
    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val words: StateFlow<VocabularyState> = kotlinx.coroutines.flow.combine(
        retry.mapLatest { runCatchingCancellable { repo.vocabulary() } }, // every word once per load, not per key
        _query.debounce(120),
    ) { all, q -> all.fold({ VocabularyState(filterVocabulary(it, q), q) }, { VocabularyState(null, q, failed = true) }) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VocabularyState(null, ""))

    fun setQuery(q: String) { _query.value = q }
    fun retry() { retry.value++ }
}

data class VocabularyState(val words: List<VocabularyWord>?, val query: String, val failed: Boolean = false)
