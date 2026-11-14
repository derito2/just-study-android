package com.dero.juststudykorean.data.games

import com.dero.juststudykorean.data.audio.KoreanVoice
import com.dero.juststudykorean.data.audio.VoiceState
import com.dero.juststudykorean.data.time.AppClock
import com.dero.juststudykorean.korean.games.GameType
import com.dero.juststudykorean.korean.games.MIN_BANK_WORDS
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundPlan
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.games.SessionInputs
import com.dero.juststudykorean.korean.games.availableGames
import com.dero.juststudykorean.korean.games.planRound
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** A session that was going on: what the ViewModel keeps in its SavedStateHandle (5.5). */
data class SessionRef(val sessionId: Long, val seed: Long, val roundIndex: Int)

data class SessionSummary(val rounds: Long, val won: Long, val bestStreak: Long, val durationSeconds: Long)

/** FASE_8_PLAN 5.1: Preparing → Playing → Feedback → Transition → Playing …; Playing ⇄ Paused; → Ended; or Empty / Failed. */
sealed interface SessionState {
    data object Preparing : SessionState
    data class Playing(val ref: SessionRef, val round: RoundSpec) : SessionState
    data class Feedback(val ref: SessionRef, val round: RoundSpec, val result: RoundResult) : SessionState
    data class Transition(val ref: SessionRef) : SessionState
    data class Paused(val ref: SessionRef, val round: RoundSpec) : SessionState
    data class Ended(val summary: SessionSummary) : SessionState
    data object Empty : SessionState
    data class Failed(val reason: String) : SessionState
}

private const val RESUME_WITHIN_MS = 30 * 60_000L // SESSION_IDLE: older than this, the session is closed (5.5)

/**
 * FASE_8_PLAN 5: one Games session. Every round is planRound(state, n) of a reproducible state (the bank, the seed,
 * the session's history, the items' progress, the games' last results; the weights' "now" is the session's start), so
 * a new engine with the same seed and index regenerates the same round after the process was killed. While a round is
 * played, the next one is prepared in [work] for the two likely outcomes (won without a mistake, lost); any other
 * result plans it then, from the same state. No Compose, no android.*.
 */
class GameSessionEngine(
    private val content: BankSource,
    private val repository: GameRepository,
    private val clock: AppClock,
    private val voice: KoreanVoice?,
    private val scope: CoroutineScope,
    private val work: CoroutineDispatcher = Dispatchers.Default,
    private val newSeed: () -> Long = { Random.nextLong() },
    /** The games this build can show (PR B: no listening games); the rest are never planned. */
    private val games: Set<GameType> = GameType.entries.toSet(),
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Preparing)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val lock = Mutex()
    private var inputs: SessionInputs? = null
    private var ref: SessionRef? = null
    private var current: RoundPlan? = null
    private var ifWon: Deferred<RoundPlan?>? = null
    private var ifLost: Deferred<RoundPlan?>? = null

    /** A new session, or [resume] the one of the SavedStateHandle (regenerating the same round). */
    suspend fun start(resume: SessionRef? = null) = lock.withLock {
        try {
            _state.value = SessionState.Preparing
            val bank = content.bank()
            val available = availableGames(bank, voice?.state?.value == VoiceState.Ready).filterKeys { it in games }
            if (bank.words.size < MIN_BANK_WORDS || available.isEmpty()) { _state.value = SessionState.Empty; return@withLock }
            val old = resume?.let { repository.session(it.sessionId) }
            val now = clock.now().toEpochMilli()
            if (resume != null && old != null && old.endedAt == null && now - (repository.lastActivity(old.id) ?: 0) > RESUME_WITHIN_MS) {
                repository.endSession(old.id) // too long ago: closed, and its summary shown
                _state.value = SessionState.Ended(summary(old.id))
                return@withLock
            }
            val (id, seed, index, startedAt) = if (resume != null && old != null && old.endedAt == null) {
                listOf(old.id, old.seed, resume.roundIndex.toLong(), old.startedAt)
            } else {
                val seed = newSeed()
                val id = repository.startSession(seed)
                listOf(id, seed, 0L, repository.session(id)!!.startedAt)
            }
            inputs = SessionInputs(bank, seed, startedAt, available, repository.history(id), repository.progress(), repository.lastOutcomes())
            play(SessionRef(id, seed, index.toInt()), withContext(work) { planRound(inputs!!, index.toInt()) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = SessionState.Failed(e.javaClass.simpleName)
        }
    }

    private fun play(at: SessionRef, plan: RoundPlan?) {
        if (plan == null) { _state.value = SessionState.Empty; return }
        ref = at
        current = plan
        _state.value = SessionState.Playing(at, plan.spec)
        val base = inputs!!
        val next = at.roundIndex + 1
        ifWon = scope.async(work) { planRound(base.after(plan.game, RoundResult(Outcome.WON, 0, 0, plan.spec.targets)), next) }
        ifLost = scope.async(work) { planRound(base.after(plan.game, RoundResult(Outcome.LOST, 0, 0, plan.spec.targets, plan.spec.targets)), next) }
    }

    /** The round's result: saved (unless skipped), shown as Feedback. */
    suspend fun submit(result: RoundResult) = lock.withLock {
        val plan = current ?: return@withLock
        val at = ref ?: return@withLock
        if (_state.value !is SessionState.Playing) return@withLock
        repository.saveRound(at.sessionId, at.roundIndex, plan.game, plan.level, result)
        _state.value = SessionState.Feedback(at, plan.spec, result)
    }

    /** After the feedback: the next round (one of the two prepared, or planned now from the same state). */
    suspend fun feedbackDone() = lock.withLock {
        val shown = _state.value as? SessionState.Feedback ?: return@withLock
        val plan = current ?: return@withLock
        val result = shown.result
        val nextRef = shown.ref.copy(roundIndex = shown.ref.roundIndex + 1)
        _state.value = SessionState.Transition(nextRef)
        inputs = inputs!!.after(plan.game, result)
        // A candidate is used only if the real result changes the state exactly as its hypothesis did.
        val sameKeys = result.itemKeys == plan.spec.targets
        val prepared = when {
            sameKeys && result.outcome == Outcome.WON && result.failedKeys.isEmpty() -> ifWon
            sameKeys && result.outcome == Outcome.LOST && result.failedKeys == plan.spec.targets -> ifLost
            else -> null
        }
        val next = prepared?.await() ?: withContext(work) { planRound(inputs!!, nextRef.roundIndex) }
        play(nextRef, next)
    }

    fun pause() {
        val playing = _state.value as? SessionState.Playing ?: return
        _state.value = SessionState.Paused(playing.ref, playing.round) // the round stays as it was
    }

    fun resume() {
        val paused = _state.value as? SessionState.Paused ?: return
        _state.value = SessionState.Playing(paused.ref, paused.round)
    }

    /** Leaving Games: everything pending is cancelled, the voice stops, the session is closed and saved. */
    suspend fun end() = lock.withLock {
        ifWon?.cancel(); ifLost?.cancel()
        voice?.stop()
        val id = ref?.sessionId ?: return@withLock
        repository.endSession(id)
        _state.value = SessionState.Ended(summary(id))
    }

    private suspend fun summary(id: Long): SessionSummary =
        repository.session(id)!!.let { SessionSummary(it.rounds, it.won, it.bestStreak, it.durationSeconds) }
}
