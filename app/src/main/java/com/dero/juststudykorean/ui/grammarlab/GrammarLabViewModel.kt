package com.dero.juststudykorean.ui.grammarlab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dero.juststudykorean.data.ai.QuizAi
import com.dero.juststudykorean.data.grammar.GrammarLabRepository
import com.dero.juststudykorean.data.grammar.LabPoint
import com.dero.juststudykorean.data.grammar.isRight
import com.dero.juststudykorean.data.prefs.UserPrefs
import com.dero.juststudykorean.korean.grammar.Drill
import com.dero.juststudykorean.korean.grammar.LabVerb
import com.dero.juststudykorean.korean.grammar.explain
import com.dero.juststudykorean.ui.components.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** After Check: what you gave, whether it is right, the rule-based explanation and, on request, the model's (AiMark). */
data class LabResult(val given: String, val correct: Boolean, val explanation: String?, val ai: String? = null, val explaining: Boolean = false)

data class GrammarLabUi(
    val loading: Boolean = true,
    val points: List<LabPoint> = emptyList(),
    val verbs: Map<String, LabVerb> = emptyMap(),
    val pointKey: String? = null,
    val level: Int = 1,
    val drills: List<Drill> = emptyList(),
    val index: Int = 0,
    val typed: String = "",
    val result: LabResult? = null,
    /** Each answered drill and whether it was right, for the summary. */
    val answers: List<Pair<Drill, Boolean>> = emptyList(),
) {
    val drill: Drill? get() = drills.getOrNull(index)
    val done: Boolean get() = !loading && drills.isNotEmpty() && index >= drills.size
    val empty: Boolean get() = !loading && drills.isEmpty()
}

/** Grammar Lab (design/beta.md 3): ten drills, Check, the result and its explanation, Next, then the summary. */
@HiltViewModel
class GrammarLabViewModel @Inject constructor(
    private val repo: GrammarLabRepository,
    private val prefs: UserPrefs,
    private val ai: QuizAi? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(GrammarLabUi())
    val state = _state.asStateFlow()
    /** Produce and FillExample: the system keyboard instead of HangulKeys, remembered. */
    val systemKeyboard: StateFlow<Boolean> = prefs.labSystemKeyboard.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private var seed = System.currentTimeMillis()
    private var loading: Job? = null

    fun aiReady() = ai?.ready() == true

    fun open(pointKey: String?, seed: Long = this.seed) {
        this.seed = seed
        _state.value = _state.value.copy(pointKey = pointKey)
        load()
    }

    private fun load() {
        loading?.cancel() // the last point or level chosen wins
        loading = viewModelScope.launch { loadNow() }
    }

    private suspend fun loadNow() {
        val s = _state.value
        _state.value = s.copy(loading = true)
        val points = repo.points()
        val verbs = repo.verbs().associateBy { it.verb }
        val drills = repo.session(s.pointKey?.takeIf { k -> points.any { it.key == k } }, s.level, seed)
        _state.value = _state.value.copy(loading = false, points = points, verbs = verbs, drills = drills, index = 0, typed = "", result = null, answers = emptyList())
    }

    fun setPoint(key: String?) { _state.value = _state.value.copy(pointKey = key); load() }

    fun setLevel(level: Int) { _state.value = _state.value.copy(level = level); load() }

    fun setSystemKeyboard(on: Boolean) = viewModelScope.launch { prefs.setLabSystemKeyboard(on) }

    fun type(text: String) { if (_state.value.result == null) _state.value = _state.value.copy(typed = text) }

    fun check(given: String = _state.value.typed) {
        val s = _state.value
        val d = s.drill ?: return
        if (s.result != null || given.isBlank()) return
        val right = isRight(d, given)
        val why = if (right) null else explain(d.verb, d.ending, s.verbs[d.verb]?.kind)
        _state.value = s.copy(typed = given, result = LabResult(given, right, why), answers = s.answers + (d to right))
        viewModelScope.launch { runCatchingCancellable { repo.save(d, given, right) } }
    }

    /** With Nano, a model explanation of the mistake from your point (use 2); none if it can't. */
    fun explainMore() {
        val s = _state.value
        val d = s.drill ?: return
        val r = s.result ?: return
        if (r.correct || r.ai != null || !aiReady()) return
        _state.value = s.copy(result = r.copy(explaining = true))
        val point = s.points.firstOrNull { it.key == d.pointKey }?.let { "${it.pattern} = ${it.meaning}" }
        val right = when (d) { is Drill.Choose -> d.answer; is Drill.Produce -> d.answers.first(); is Drill.FillExample -> d.answers.first() }
        viewModelScope.launch {
            val text = runCatchingCancellable { ai!!.explainMistake("${d.verb} + ${d.ending.label}", right, r.given, point) }.getOrNull()
            _state.value.result?.let { _state.value = _state.value.copy(result = it.copy(ai = text, explaining = false)) }
        }
    }

    fun next() {
        val s = _state.value
        if (s.result == null) return
        _state.value = s.copy(index = s.index + 1, typed = "", result = null)
    }

    /** The summary's "Practice the ones I missed": the same drills you got wrong, again. */
    fun practiceMissed() {
        val missed = _state.value.answers.filterNot { it.second }.map { it.first }
        if (missed.isNotEmpty()) _state.value = _state.value.copy(drills = missed, index = 0, typed = "", result = null, answers = emptyList())
    }
}
