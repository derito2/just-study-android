package com.dero.juststudykorean.data.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ai.AppForeground
import com.dero.juststudykorean.ai.NanoGate
import com.dero.juststudykorean.ai.NanoModel
import com.dero.juststudykorean.ai.NanoState
import com.dero.juststudykorean.ai.NanoStatus
import com.dero.juststudykorean.data.golden.GoldenDb
import com.dero.juststudykorean.data.grammar.GrammarFilter
import com.dero.juststudykorean.data.grammar.GrammarRepository
import com.dero.juststudykorean.data.notes.NotesSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Phase 6, task 6 (uses 7 and 8) with a fake model on the golden database. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class GrammarAiTest : GoldenDb() {
    private class Fake(var reply: (String) -> String) : NanoModel {
        override suspend fun status() = NanoStatus.Available
        override fun download() = emptyFlow<NanoStatus>()
        override suspend fun generate(system: String, prompt: String, maxTokens: Int, temperature: Float?) = reply(prompt)
    }

    private fun ai(model: Fake): GrammarAi {
        AppForeground.visible = true
        val state = NanoState(model).also { it.refresh() }
        runTest { state.status.first { it == NanoStatus.Available } }
        return GrammarAi(NanoGate(model, state) { false }, db, NotesSource(context))
    }

    private fun point() = runBlocking { GrammarRepository(db).points(GrammarFilter.All, "").first().first { it.examples.isNotEmpty() } }

    @Test
    fun simplerQuotesOnlyThePoint() = runBlocking {
        val p = point()
        assertNotNull(ai(Fake { "This pattern ${p.pattern} is simple: it is used as in your notes, nothing more to it." }).simpler(p))
        assertNull(ai(Fake { "It works like 포도를 먹었어요, a very common sentence in daily life." }).simpler(p.copy(id = p.id + 1000)))
    }

    @Test
    fun practiceWithAWordThatIsNotInYourNotesIsDropped() = runBlocking {
        val p = point()
        assertNull(ai(Fake { "KOREAN: 우주선을 타요.\nENGLISH: I ride a spaceship." }).practice(p))
    }

}
