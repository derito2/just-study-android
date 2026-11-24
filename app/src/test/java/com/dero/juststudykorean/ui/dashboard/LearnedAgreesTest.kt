package com.dero.juststudykorean.ui.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.FlashcardReviewsEntity
import com.dero.juststudykorean.korean.norm
import com.dero.juststudykorean.ui.flashcards.DecksViewModel
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration

/** D11-1 (FASE_11_PLAN PR 1): Flashcards' "Learned" and Dashboard's "Words learned this week" count the same words. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class LearnedAgreesTest {
    private val app = GoldenApp()
    private val scope = CoroutineScope(Dispatchers.IO)

    @After
    fun close() { scope.cancel(); app.close() }

    private fun learned(): Int {
        val vm = DecksViewModel(app.flashcards, app.prefs, app.words)
        val job = scope.launch { vm.state.collect { } }
        idleUntil { vm.state.value.stats != null }
        return vm.state.value.stats!!.learned.also { job.cancel() }
    }

    private fun thisWeek(): Pair<Int, Int?> {
        val vm = DashboardViewModel(app.progress, app.quiz, app.prefs, app.words, app.clock)
        val job = scope.launch { vm.state.collect { } }
        idleUntil { vm.state.value.weekly != null }
        return vm.state.value.weekly!!.let { it.wordsThisWeek to it.wordsLastWeek }.also { job.cancel() }
    }

    @Test
    fun bothScreensUseTheSameWords() {
        // Golden base: 4 words learned, 1 of them this week and 2 the week before (the old rules said 26 and other weeks).
        assertEquals(4, learned())
        assertEquals(1 to 2, thisWeek())

        runBlocking { // a word learned today: one more in both places
            val all = app.words.all()
            val card = app.db.flashcardsDao().all().first { all[norm(it.koreanWord)]?.status?.mastered == false }
            app.db.flashcardsDao().update(card.copy(intervalHours = 600))
            var id = app.db.flashcardReviewsDao().all().maxOf { it.id }
            val now = app.clock.now().toEpochMilli()
            for (i in 4 downTo 0) app.db.flashcardReviewsDao().insert(FlashcardReviewsEntity(++id, card.id, "good", now - Duration.ofHours(i + 1L).toMillis(), 100, 600))
        }
        assertEquals(5, learned())
        assertEquals(2 to 2, thisWeek())
    }
}
