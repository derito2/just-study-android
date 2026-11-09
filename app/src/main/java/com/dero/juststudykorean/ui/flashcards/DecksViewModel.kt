package com.dero.juststudykorean.ui.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.flashcards.Deck
import com.dero.juststudykorean.data.flashcards.DeletedDeck
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.flashcards.FlashcardStats
import com.dero.juststudykorean.data.flashcards.ReviewItem
import com.dero.juststudykorean.data.prefs.UserPrefs
import com.dero.juststudykorean.korean.Scheduling
import com.dero.juststudykorean.ui.components.plural
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DecksUi(
    val decks: List<Deck>? = null,
    val stats: FlashcardStats? = null,
    val history: List<ReviewItem> = emptyList(),
    val documents: List<DocumentsEntity> = emptyList(),
    val error: Boolean = false,
    val creating: Boolean = false,
    val notice: String? = null,
)

/** A Snackbar; [undo] puts a deleted deck back. */
data class DecksMessage(val text: String, val undo: DeletedDeck? = null)

private class Loaded(val decks: List<Deck>, val stats: FlashcardStats, val history: List<ReviewItem>, val documents: List<DocumentsEntity>)

/** Decks (design/flashcards.md A and B): counters, decks, edit mode, create cards and review history. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class DecksViewModel @Inject constructor(private val repo: FlashcardRepository, private val prefs: UserPrefs) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val creating = MutableStateFlow(false)
    private val notice = MutableStateFlow<String?>(null)
    private val messages = Channel<DecksMessage>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    val scheduling: StateFlow<Scheduling> = prefs.scheduling.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Scheduling())

    private val loaded = retry.flatMapLatest {
        repo.decks().map<List<Deck>, Loaded?> { decks -> Loaded(decks, repo.statsNow(), repo.history(30), repo.readyDocuments()) }.catch { emit(null) }
    }

    val state: StateFlow<DecksUi> = combine(loaded, creating, notice) { data, busy, note ->
        DecksUi(data?.decks, data?.stats, data?.history.orEmpty(), data?.documents.orEmpty(), error = data == null, creating = busy, notice = note)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DecksUi())

    fun retry() { retry.value++ }

    /** A new name; an empty one keeps the old name (nothing is saved). */
    fun rename(deck: Deck, name: String) = viewModelScope.launch {
        val clean = name.trim()
        if (clean.isEmpty() || clean == deck.name) return@launch
        runCatching { repo.rename(deck.id, clean) }.onFailure { messages.send(DecksMessage(it.message ?: "Couldn't rename the deck.")) }
    }

    fun reorder(ids: List<Long>) = viewModelScope.launch {
        runCatching { repo.reorder(ids) }.onFailure { messages.send(DecksMessage(it.message ?: "Couldn't move the deck.")) }
    }

    /** Combines [ids] (the target first) under [name]; false without a name. */
    fun merge(ids: List<Long>, name: String): Boolean {
        if (name.isBlank()) return false
        viewModelScope.launch {
            runCatching { repo.merge(ids, name) }
                .onSuccess { messages.send(DecksMessage("Combined into \"${name.trim()}\".")) }
                .onFailure { messages.send(DecksMessage(it.message ?: "Couldn't combine the decks.")) }
        }
        return true
    }

    fun delete(deck: Deck) = viewModelScope.launch {
        runCatching { repo.delete(deck.id) }
            .onSuccess { messages.send(DecksMessage("Deleted ${it.cards.size} cards from ${deck.name}.", undo = it)) }
            .onFailure { messages.send(DecksMessage(it.message ?: "Couldn't delete the deck.")) }
    }

    fun undo(deleted: DeletedDeck) = viewModelScope.launch { repo.undoDelete(deleted) }

    /** Create cards (the web's messages); [documentIds] empty means all documents. */
    fun createCards(documentIds: List<Long>) = viewModelScope.launch {
        if (creating.value) return@launch
        creating.value = true
        notice.value = null
        try {
            val r = repo.createCards(documentIds)
            notice.value = if (r.created > 0) {
                "Created ${plural(r.created, "new card")}" + if (r.skipped > 0) " (${plural(r.skipped, "word")} already had a card)." else "."
            } else {
                "No new cards: every word in ${if (documentIds.isNotEmpty()) "those documents" else "your documents"} already has a card in its deck. Continue it below."
            }
        } catch (e: Exception) {
            notice.value = e.message ?: "Couldn't create cards."
        } finally {
            creating.value = false
        }
    }

    /** Review options; null and a message when a value is out of range (the web's checks). */
    fun saveScheduling(values: List<String>): String? {
        val maxes = listOf(1440, 8760, 8760, 8760)
        val names = listOf("Again", "Hard", "Good", "Easy")
        val parsed = values.mapIndexed { i, v ->
            v.trim().toIntOrNull()?.takeIf { it in 1..maxes[i] } ?: return "${names[i]} must be a whole number from 1 to ${maxes[i]}."
        }
        viewModelScope.launch { prefs.setScheduling(Scheduling(parsed[0], parsed[1], parsed[2], parsed[3])) }
        return null
    }
}
