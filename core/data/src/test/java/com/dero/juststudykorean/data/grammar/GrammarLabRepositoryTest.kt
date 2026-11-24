package com.dero.juststudykorean.data.grammar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.golden.GoldenDb
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.korean.grammar.Drill
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F3 (FASE_11_PLAN PR 6): Grammar Lab's data on the golden base. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class GrammarLabRepositoryTest : GoldenDb() {
    private val repo by lazy { GrammarLabRepository(db, GrammarRepository(db), NotesSource(context), clock) }

    @Test
    fun onlyPointsWithAnEndingAndVerbsOfYourNotes() = runBlocking {
        val points = repo.points()
        assertTrue("some golden points are endings", points.isNotEmpty())
        assertTrue(points.all { it.key.isNotEmpty() })
        assertEquals(points.size, points.map { it.key }.toSet().size)
        val verbs = repo.verbs()
        assertTrue("the golden notes have verbs", verbs.isNotEmpty())
        assertTrue(verbs.all { it.verb.endsWith("다") })
    }

    @Test
    fun aSessionHasTenDrillsAndTheSameSeedGivesTheSame() = runBlocking {
        val a = repo.session(null, level = 2, seed = 5)
        assertTrue(a.size in 1..10)
        assertEquals(a, repo.session(null, level = 2, seed = 5))
        val one = repo.points().first().key
        assertTrue(repo.session(one, level = 2, seed = 5).all { it.pointKey == one })
    }

    @Test
    fun attemptsAreSavedAndWhatYouMissedComesFirst() = runBlocking {
        val drills = repo.session(null, level = 2, seed = 9)
        val missed = drills.last()
        repo.save(missed, "틀림", correct = false)
        repo.save(missed, "틀림", correct = false)
        repo.save(drills.first(), "맞음", correct = true)
        assertEquals(3, db.grammarDrillAttemptsDao().count())
        val next = repo.session(null, level = 2, seed = 9)
        assertEquals(missed.verb to missed.pointKey, next.first().let { it.verb to it.pointKey })
    }

    @Test
    fun theAnswerIsCheckedWithoutSpacesOrPunctuation() {
        val d = Drill.Produce("k", "가다", com.dero.juststudykorean.korean.grammar.Ending.Future, setOf("갈 거예요"))
        assertTrue(isRight(d, "갈거예요."))
        assertTrue(!isRight(d, "가 거예요"))
        val c = Drill.Choose("k", "가다", com.dero.juststudykorean.korean.grammar.Ending.SoBecause, listOf("가서", "가아서"), "가서")
        assertTrue(isRight(c, "가서") && !isRight(c, "가아서"))
    }
}
