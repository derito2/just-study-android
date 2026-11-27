package com.dero.juststudykorean.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dero.juststudykorean.data.ai.ReaderAi
import com.dero.juststudykorean.data.audio.KoreanVoice
import com.dero.juststudykorean.data.audio.KoreanVoices
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.flashcards.Deck
import com.dero.juststudykorean.data.reader.ReaderPage
import com.dero.juststudykorean.data.reader.ReaderRepository
import com.dero.juststudykorean.korean.reader.Gloss
import com.dero.juststudykorean.korean.reader.ReaderNotes
import com.dero.juststudykorean.korean.reader.lookup
import com.dero.juststudykorean.korean.reader.tokens
import com.dero.juststudykorean.ui.components.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A long-pressed line: Explain this sentence (the model's, or each word with its meaning), Hear sentence and Copy. */
data class SentenceUi(val line: String, val loading: Boolean = false, val explanation: String? = null, val glosses: List<Pair<String, String>>? = null)

data class ReaderUi(
    val loading: Boolean = true,
    val documents: List<DocumentsEntity> = emptyList(),
    val document: DocumentsEntity? = null,
    val pages: List<ReaderPage> = emptyList(),
    /** The tapped word's sheet. */
    val gloss: Gloss? = null,
    /** Add to deck: your decks (New deck goes on top). */
    val decks: List<Deck>? = null,
    /** "Added to {deck}." or why not. */
    val added: String? = null,
    val sentence: SentenceUi? = null,
)

/** The word a gloss is about, and its meaning in your notes (null: not in them). */
fun Gloss.word(): String = when (this) {
    is Gloss.Entry -> korean
    is Gloss.Inflected -> surface
    is Gloss.Grammar -> pattern
    is Gloss.NotFound -> surface
}

fun Gloss.meaning(): String? = when (this) {
    is Gloss.Entry -> english
    is Gloss.Inflected -> entry?.english
    is Gloss.Grammar -> meaning
    is Gloss.NotFound -> null
}

/** Reader (design/beta.md 5, FASE_11_PLAN PR 8): your PDFs' extracted text; tap a word, long-press a line. Not study time (D11-2). */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repo: ReaderRepository,
    voices: KoreanVoices,
    private val ai: ReaderAi? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(ReaderUi())
    val state = _state.asStateFlow()
    private val voice: KoreanVoice = voices.create()
    private var opened = false
    private var startDocument: Long? = null
    private lateinit var notes: Deferred<ReaderNotes>
    /** The tapped word as it was on the page (what 🔊 says). */
    private var tapped = ""

    fun open(documentId: Long?) {
        if (opened) return
        opened = true
        startDocument = documentId
        notes = viewModelScope.async { repo.notes() }
        viewModelScope.launch {
            val docs = repo.documents()
            val doc = docs.firstOrNull { it.id == documentId }
            _state.value = _state.value.copy(loading = false, documents = docs, document = doc, pages = doc?.let { repo.pages(it.id) }.orEmpty())
        }
    }

    fun choose(doc: DocumentsEntity) = viewModelScope.launch {
        _state.value = _state.value.copy(document = doc, pages = repo.pages(doc.id))
    }

    /** Back from a document to the list; false when the screen itself should close. */
    fun back(): Boolean {
        if (_state.value.document == null || startDocument != null) return false
        _state.value = _state.value.copy(document = null, pages = emptyList())
        return true
    }

    fun tap(word: String) = viewModelScope.launch {
        tapped = word
        val gloss = withContext(Dispatchers.Default) { lookup(word, notes.await()) }
        _state.value = _state.value.copy(gloss = gloss, added = null, decks = null, sentence = null)
    }

    fun closeSheet() { _state.value = _state.value.copy(gloss = null, decks = null, added = null, sentence = null) }

    private fun say(text: String) = viewModelScope.launch { voice.stop(); runCatchingCancellable { voice.speak(text, 1f) } }

    fun speakWord() { if (tapped.isNotEmpty()) say(tapped) }

    /** The word Add to deck adds: the entry's Korean (먹었어요 adds 먹다). */
    private fun cardWord(): String? = when (val g = _state.value.gloss) {
        is Gloss.Entry -> g.korean
        is Gloss.Inflected -> g.entry?.korean
        else -> null
    }

    fun chooseDeck() = viewModelScope.launch { _state.value = _state.value.copy(decks = repo.decks()) }

    fun addTo(deckId: Long?) = viewModelScope.launch {
        val word = cardWord() ?: return@launch
        val text = runCatchingCancellable {
            val id = repo.addTo(deckId, word)
            "Added to ${repo.decks().firstOrNull { it.id == id }?.name ?: "your deck"}."
        }.getOrElse { it.message ?: "Couldn't add it." }
        _state.value = _state.value.copy(decks = null, added = text)
    }

    fun longPress(line: String) { _state.value = _state.value.copy(sentence = SentenceUi(line), gloss = null, decks = null, added = null) }

    fun hearSentence() { _state.value.sentence?.let { say(it.line) } }

    fun explain() = viewModelScope.launch {
        val line = _state.value.sentence?.line ?: return@launch
        _state.value = _state.value.copy(sentence = SentenceUi(line, loading = true))
        val mine = notes.await()
        val glosses = withContext(Dispatchers.Default) {
            tokens(line).map { it.text }.distinct().map { w -> w to (lookup(w, mine).let { g -> g.meaning()?.let { m -> "${g.lemma()} · $m" } } ?: "Not in your notes.") }
        }
        // Its translation, when your notes have this sentence as "Korean = English".
        val translation = mine.sentences.firstOrNull { it.substringBefore("=").trim() == line.substringBefore("=").trim() }?.substringAfter("=", "")?.trim()?.ifEmpty { null }
        val explanation = if (ai?.ready() == true) ai.explain(line, translation, glosses.filter { it.second != "Not in your notes." }.map { "${it.first} = ${it.second}" }) else null
        if (_state.value.sentence?.line == line) _state.value = _state.value.copy(sentence = SentenceUi(line, explanation = explanation, glosses = glosses))
    }

    override fun onCleared() = voice.release()
}

/** The base form shown next to a meaning: 학교 for 학교에서, 먹다 for 먹었어요. */
fun Gloss.lemma(): String = when (this) {
    is Gloss.Inflected -> lemma
    else -> word()
}
