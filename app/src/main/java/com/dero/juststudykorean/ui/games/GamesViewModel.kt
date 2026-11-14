package com.dero.juststudykorean.ui.games

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dero.juststudykorean.data.games.BankSource
import com.dero.juststudykorean.data.games.GameContentProvider
import com.dero.juststudykorean.data.games.GameRepository
import com.dero.juststudykorean.data.games.GameSessionEngine
import com.dero.juststudykorean.data.games.SessionRef
import com.dero.juststudykorean.data.games.SessionState
import com.dero.juststudykorean.data.prefs.UserPrefs
import com.dero.juststudykorean.data.time.AppClock
import com.dero.juststudykorean.korean.games.GameItem
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.ui.components.runCatchingCancellable
import com.dero.juststudykorean.korean.games.GameBank
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the bar, the pause sheet and the summary count in this session (design/games.md 5, 9, 10). */
data class SessionCounts(
    val rounds: Int = 0,
    val won: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val seconds: Long = 0,
    /** Grows at streak 3, 5, 10 and every 10 after: the petals (7.3). */
    val burst: Int = 0,
    /** Lost rounds in a row: after two, the next "Not quite" says "Almost. Keep going." (7.2). */
    val lostInARow: Int = 0,
    /** The items you failed in the session, newest last (the summary shows up to 5). */
    val failed: List<GameItem> = emptyList(),
)

private const val ROUND_CAP_MS = 120_000L // as the repository: a round untouched for 2 min counts 2 min
private const val SESSION_KEY = "games_session"

/**
 * Games' screen state over [GameSessionEngine] (FASE_8_PLAN 5): the engine plays, this counts what the screen shows and
 * keeps the session in the SavedStateHandle (5.5). No listening games in this PR: the engine gets no voice.
 */
@HiltViewModel
class GamesViewModel @Inject constructor(
    private val content: GameContentProvider,
    repository: GameRepository,
    private val clock: AppClock,
    private val prefs: UserPrefs,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val source: BankSource = content
    private val engine = GameSessionEngine(source, repository, clock, voice = null, scope = viewModelScope, games = PLAYABLE)
    val state: StateFlow<SessionState> = engine.state
    private val _counts = MutableStateFlow(SessionCounts())
    val counts: StateFlow<SessionCounts> = _counts.asStateFlow()
    val typingTimer: StateFlow<Boolean> = prefs.typingTimer.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    private val _bank = MutableStateFlow(GameBank(emptyList(), emptyList()))
    val bank: StateFlow<GameBank> = _bank.asStateFlow()
    private var pausedAt: Long? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            val ref = saved.get<String>(SESSION_KEY)?.split(":")?.let { (id, seed, n) -> SessionRef(id.toLong(), seed.toLong(), n.toInt()) }
            runCatchingCancellable { engine.start(ref) }
            runCatchingCancellable { content.bank() }.onSuccess { _bank.value = it } // cached by the provider
            remember()
        }
    }

    private fun remember() {
        (state.value as? SessionState.Playing)?.ref?.let { saved[SESSION_KEY] = "${it.sessionId}:${it.seed}:${it.roundIndex}" }
    }

    fun submit(result: RoundResult) = viewModelScope.launch {
        val bank = runCatchingCancellable { source.bank() }.getOrNull()
        _counts.update { c ->
            val graded = result.outcome != Outcome.SKIPPED
            val won = result.outcome == Outcome.WON
            val streak = when (result.outcome) { Outcome.WON -> c.streak + 1; Outcome.LOST -> 0; else -> c.streak }
            val burst = if (won && (streak in setOf(3, 5, 10) || (streak > 10 && streak % 10 == 0))) c.burst + 1 else c.burst
            val failed = result.failedKeys.mapNotNull { bank?.byKey?.get(it) }
            c.copy(
                rounds = c.rounds + if (graded) 1 else 0, won = c.won + if (won) 1 else 0, streak = streak, bestStreak = maxOf(c.bestStreak, streak),
                seconds = c.seconds + if (graded) (minOf(result.responseMs, ROUND_CAP_MS) + 500) / 1000 else 0, burst = burst,
                lostInARow = if (result.outcome == Outcome.LOST) c.lostInARow + 1 else if (won) 0 else c.lostInARow,
                failed = (c.failed + failed).distinctBy { it.key },
            )
        }
        engine.submit(result)
    }

    fun feedbackDone() = viewModelScope.launch { engine.feedbackDone(); remember() }

    fun pause() { engine.pause(); if (pausedAt == null && state.value is SessionState.Paused) pausedAt = clock.now().toEpochMilli() }

    /** After more than 30 min paused, the session closes itself and the summary shows (games.md 9). */
    fun resume() {
        val since = pausedAt
        pausedAt = null
        if (since != null && clock.now().toEpochMilli() - since > 30 * 60_000L) end() else engine.resume()
    }

    fun end() = viewModelScope.launch { engine.end() }

    /** Play again: a new session, the counts from zero. */
    fun playAgain() {
        saved.remove<String>(SESSION_KEY)
        _counts.value = SessionCounts()
        viewModelScope.launch { runCatchingCancellable { engine.start() }; remember() }
    }

    fun setTypingTimer(on: Boolean) = viewModelScope.launch { prefs.setTypingTimer(on) }
}
