package com.dero.juststudykorean.ui.grammar

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import com.dero.juststudykorean.ui.components.runCatchingCancellable
import com.dero.juststudykorean.data.ai.GrammarAi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dero.juststudykorean.data.grammar.GrammarFilter
import com.dero.juststudykorean.data.grammar.GrammarPointUi
import com.dero.juststudykorean.data.grammar.GrammarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GrammarUi(
    val filter: GrammarFilter = GrammarFilter.All,
    val query: String = "",
    /** null while it loads. */
    val points: List<GrammarPointUi>? = null,
    /** Any grammar at all in your notes (else the empty state). */
    val any: Boolean = true,
    val open: Long? = null,
    val error: Boolean = false,
    /** Gemini Nano Available: the open card shows Explain it simpler and Practice sentence (phase 6, use 7). */
    val ai: Boolean = false,
    val written: Map<Long, GrammarWritten> = emptyMap(),
)

/** What the model wrote for a point (never saved): a simpler explanation and a practice sentence (Korean only, R1-18). */
data class GrammarWritten(val busy: Boolean = false, val simpler: String? = null, val practice: String? = null)

/** Grammar (design/grammar-calendar-settings.md): filter pills, search as you type, one card open at a time. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GrammarViewModel @Inject constructor(private val repo: GrammarRepository, private val grammarAi: GrammarAi? = null) : ViewModel() {
    private val written = MutableStateFlow<Map<Long, GrammarWritten>>(emptyMap())
    private val filter = MutableStateFlow(GrammarFilter.All)
    private val query = MutableStateFlow("")
    private val open = MutableStateFlow<Long?>(null)
    private val retry = MutableStateFlow(0)

    private val points = combine(filter, query, retry) { f, q, _ -> f to q }.flatMapLatest { (f, q) ->
        repo.points(f, q).map<List<GrammarPointUi>, List<GrammarPointUi>?> { it }.catch { emit(null) }
    }
    private val any = retry.flatMapLatest { repo.points(GrammarFilter.All, "").map { it.isNotEmpty() }.catch { emit(true) } }

    val state: StateFlow<GrammarUi> = combine(combine(filter, query, points, any, open) { f, q, p, a, o ->
        GrammarUi(f, q, p, a, o, error = p == null) // combine emits once points did: null here is a failure
    }, written) { ui, w -> ui.copy(ai = grammarAi?.ready() == true, written = w) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrammarUi())

    fun setFilter(f: GrammarFilter) { filter.value = f; open.value = null }
    fun setQuery(q: String) { query.value = q }

    /** Opens a card and closes the one that was open; tapping the open one closes it. */
    fun toggle(id: Long) { open.value = if (open.value == id) null else id }

    fun retry() { retry.value++ }

    /** 7a / 7b: one at a time per point; nothing is saved. */
    fun explainSimpler(id: Long) = write(id) { p, w -> w.copy(simpler = grammarAi!!.simpler(p) ?: w.simpler) }
    fun practiceSentence(id: Long) = write(id) { p, w -> w.copy(practice = grammarAi!!.practice(p) ?: w.practice) }

    private fun write(id: Long, block: suspend (GrammarPointUi, GrammarWritten) -> GrammarWritten) {
        val p = state.value.points?.firstOrNull { it.id == id } ?: return
        val now = written.value[id] ?: GrammarWritten()
        if (grammarAi == null || now.busy) return
        written.update { it + (id to now.copy(busy = true)) }
        viewModelScope.launch {
            val done = runCatchingCancellable { block(p, now) }.getOrDefault(now)
            written.update { it + (id to done.copy(busy = false)) }
        }
    }
}
