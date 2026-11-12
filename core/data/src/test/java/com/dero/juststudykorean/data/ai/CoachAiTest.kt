package com.dero.juststudykorean.data.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ai.AppForeground
import com.dero.juststudykorean.ai.NanoGate
import com.dero.juststudykorean.ai.NanoModel
import com.dero.juststudykorean.ai.NanoState
import com.dero.juststudykorean.ai.NanoStatus
import com.dero.juststudykorean.data.coach.CoachEngine
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.golden.GoldenDb
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Phase 6, task 4 (use 5) on the golden database: the model only rewrites the facts; the proposals stay the rules'. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class CoachAiTest : GoldenDb() {
    /** Repeats the facts' first line as a sentence for you; with [invent], adds a number the rules never computed. */
    private class Fake(var invent: Boolean = false) : NanoModel {
        override suspend fun status() = NanoStatus.Available
        override fun download() = emptyFlow<NanoStatus>()
        override suspend fun generate(system: String, prompt: String, maxTokens: Int, temperature: Float?): String =
            if (invent) "Today you should review 987 flashcards and rest after that." else "Here is what you can do today: " + prompt.substringAfter("Facts:\n").lines().first()
    }

    private fun coach() = NotesSource(context).let { notes ->
        val progress = ProgressRepository(db, clock)
        CoachEngine(db, clock, progress, FlashcardRepository(db, clock, progress, notes), notes)
    }

    private fun ai(model: Fake): CoachAi {
        AppForeground.visible = true
        val state = NanoState(model).also { it.refresh() }
        runBlocking { repeat(50) { if (state.status.value != NanoStatus.Available) delay(20) } }
        return CoachAi(NanoGate(model, state) { false }, db)
    }

    @Test
    fun theModelRewritesOnlyTheFacts() = runBlocking {
        val rules = coach().ask("Quiz on my mistakes")
        val written = ai(Fake()).write(rules)
        assertTrue(written.ai)
        assertEquals(rules.copy(text = written.text, ai = true), written) // proposals and steps unchanged
        assertFalse(ai(Fake(invent = true)).write(coach().ask("What should I study?")).ai) // a new number: the template
    }

    @Test
    fun aWordsEntryStaysTheRules() = runBlocking {
        val entry = coach().ask("What does 근처 mean?")
        assertEquals(entry, ai(Fake()).write(entry))
    }
}
