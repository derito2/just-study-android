package com.dero.juststudykorean.data.games

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.audio.KoreanVoice
import com.dero.juststudykorean.data.audio.VoiceState
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.time.FixedClock
import com.dero.juststudykorean.korean.games.GameBank
import com.dero.juststudykorean.korean.games.GameItem
import com.dero.juststudykorean.korean.games.ItemKind
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.norm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** FASE_8_PLAN task 4: the session engine with a fake clock and a fake voice. Invented content only. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class GameSessionEngineTest {
    private class FakeKoreanVoice(ready: Boolean) : KoreanVoice {
        override val state = MutableStateFlow(if (ready) VoiceState.Ready else VoiceState.Missing)
        var stopped = 0
        override suspend fun speak(text: String, rate: Float) = Unit
        override fun stop() { stopped++ }
        override fun release() = Unit
    }

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbs = mutableListOf<JustStudyDatabase>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = FixedClock(Instant.parse("2026-11-14T18:00:00Z"), ZoneId.of("America/Mexico_City"))

    private fun w(k: String, m: String) = GameItem("w:${norm(k)}", ItemKind.WORD, k, listOf(k), m, k.map { it.toString() }, 1, "invented.pdf", 1)
    private fun s(k: String, m: String) = GameItem("s:${norm(k)}", ItemKind.SENTENCE, k, listOf(k), m, k.filter { it in '가'..'힣' }.map { it.toString() }, 1, "invented.pdf", 2)

    private val bank = GameBank(
        listOf(
            w("사과", "Apple"), w("우유", "Milk"), w("빵", "Bread"), w("커피", "Coffee"), w("주스", "Juice"), w("과자", "Snack"),
            w("빨강", "Red"), w("파랑", "Blue"), w("노랑", "Yellow"), w("고양이", "Cat"), w("강아지", "Puppy"), w("토끼", "Rabbit"),
            w("호랑이", "Tiger"), w("엄마", "Mom"), w("아빠", "Dad"), w("동생", "Younger sibling"), w("가다", "To go"), w("먹다", "To eat"),
            w("읽다", "To read"), w("자다", "To sleep"), w("마시다", "To drink"), w("크다", "To be big"), w("작다", "To be small"),
            w("좋다", "To be good"), w("학교", "School"), w("책", "Book"), w("물", "Water"), w("집", "House"), w("닭", "Chicken"),
            w("달", "Moon"), w("딸", "Daughter"), w("교실", "Classroom"), w("선생님", "Teacher"), w("친구", "Friend"), w("도서관", "Library"),
            w("병원", "Hospital"), w("화장실", "Bathroom"), w("자동차", "Car"), w("비행기", "Airplane"), w("지하철역", "Subway station"),
            w("손", "Hand"), w("발", "Foot"), w("꽃", "Flower"), w("의자", "Chair"),
        ),
        listOf(
            s("사과를 먹어요.", "I eat an apple"), s("책을 읽어요.", "I read a book"), s("고양이가 자요.", "The cat sleeps"),
            s("친구와 학교에 가요.", "I go to school with a friend"), s("엄마는 커피를 마셔요.", "Mom drinks coffee"),
            s("동생이 우유를 마셔요.", "My younger sibling drinks milk"), s("선생님이 교실에서 책을 읽어요.", "The teacher reads a book in the classroom"),
            s("아빠는 집으로 와요.", "Dad comes home"), s("강아지가 빵을 먹어요.", "The puppy eats bread"), s("우리 집은 학교 옆에 있어요.", "Our house is next to the school"),
        ),
    )

    @After
    fun close() { scope.cancel(); dbs.forEach { it.close() } }

    private fun db() = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build().also { dbs += it }

    private fun engine(db: JustStudyDatabase, voice: Boolean = true, seed: Long = 42) =
        GameSessionEngine({ bank }, GameRepository(db, clock), clock, FakeKoreanVoice(voice), scope, Dispatchers.Default) { seed }

    private fun result(spec: RoundSpec, won: Boolean) =
        RoundResult(if (won) Outcome.WON else Outcome.LOST, if (won) 0 else 1, 3000, spec.targets, if (won) emptyList() else spec.targets)

    private suspend fun GameSessionEngine.playing() = state.value as SessionState.Playing

    /** Plays [n] rounds, won or lost by [outcomes]; returns the rounds shown. */
    private suspend fun GameSessionEngine.play(n: Int, outcomes: Random): List<RoundSpec> {
        val shown = mutableListOf<RoundSpec>()
        repeat(n) {
            val round = playing().round
            shown += round
            submit(result(round, outcomes.nextInt(3) != 0))
            feedbackDone()
        }
        return shown
    }

    @Test
    fun threeHundredRoundsKeepTheRules() = runBlocking {
        val e = engine(db(), voice = false)
        e.start()
        val rounds = e.play(300, Random(5))
        rounds.zipWithNext().forEach { (a, b) -> assertNotEquals(a.game, b.game) }
        rounds.forEachIndexed { i, r -> rounds.subList(maxOf(0, i - 8), i).forEach { prev -> assertTrue((r.targets intersect prev.targets.toSet()).isEmpty()) } }
        assertTrue(rounds.none { it.game.needsVoice })
        assertTrue(rounds.map { it.game }.toSet().size >= 8)
    }

    @Test
    fun theSameSeedGivesTheSameSession() = runBlocking {
        val a = engine(db()).also { it.start() }.play(40, Random(1))
        val b = engine(db()).also { it.start() }.play(40, Random(1))
        assertEquals(a, b)
    }

    @Test
    fun pausingKeepsTheRoundAndEndClosesAndSaves() = runBlocking {
        val database = db()
        val e = engine(database)
        e.start()
        e.play(3, Random(2))
        val round = e.playing().round
        e.pause()
        assertTrue(e.state.value is SessionState.Paused)
        e.resume()
        assertEquals(round, e.playing().round)
        e.end()
        val ended = e.state.value as SessionState.Ended
        assertEquals(3L, ended.summary.rounds)
        assertTrue(GameRepository(database, clock).session(1)!!.endedAt != null)
    }

    @Test
    fun aKilledSessionRegeneratesTheSameRound() = runBlocking {
        val database = db()
        val first = engine(database)
        first.start()
        first.play(5, Random(3))
        val shown = first.playing()
        // The process dies here; a new engine starts from the SavedStateHandle (session, seed and round index).
        val again = engine(database, seed = 999)
        again.start(shown.ref)
        assertEquals(shown.round, again.playing().round)
        assertEquals(shown.ref, again.playing().ref)
    }

    @Test
    fun withoutEnoughWordsTheSessionIsEmpty() = runBlocking {
        val e = GameSessionEngine({ GameBank(bank.words.take(5), emptyList()) }, GameRepository(db(), clock), clock, null, scope)
        e.start()
        assertEquals(SessionState.Empty, e.state.value)
    }
}
