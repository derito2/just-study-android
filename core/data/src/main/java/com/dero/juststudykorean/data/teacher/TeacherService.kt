package com.dero.juststudykorean.data.teacher

import android.util.Log
import com.dero.juststudykorean.ai.AiRequest
import com.dero.juststudykorean.ai.AiUse
import com.dero.juststudykorean.ai.NanoGate
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.korean.ai.AI_MODEL
import com.dero.juststudykorean.korean.ai.TEACHER_AI_NO_SOURCES_VERSION
import com.dero.juststudykorean.korean.ai.TEACHER_AI_SOURCES
import com.dero.juststudykorean.korean.ai.TEACHER_AI_VERSION
import com.dero.juststudykorean.korean.ai.teacherNoSourcesPrompt
import com.dero.juststudykorean.korean.ai.teacherPrompt
import com.dero.juststudykorean.korean.ai.validNoSources
import com.dero.juststudykorean.korean.ai.validTeacher
import com.dero.juststudykorean.korean.teacher.Teacher
import com.dero.juststudykorean.korean.teacher.TeacherAnswer
import com.dero.juststudykorean.korean.teacher.formatSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** The web's tutor.schemas: a question has 2 to 500 characters. */
const val QUESTION_MIN = 2
const val QUESTION_MAX = 500

/** The rules' "your notes don't cover this" and "nothing close enough": where the model may answer (use 4). */
private val MODEL_STATUSES = setOf("not_found", "no_context")

/**
 * Teacher over your notes (notes_fts), one question at a time, its index rebuilt when a PDF is added or removed. Phase 6
 * (use 4): when the rules have no answer, Gemini Nano explains from at most 4 fragments with valid citations, or, with
 * none, on its own (no Korean, no sources). Without the model, the web's messages.
 */
@Singleton
class TeacherService @Inject constructor(db: JustStudyDatabase, private val gate: NanoGate? = null) {
    private val search = NotesSearch(db)
    private val teacher = Teacher(search)
    private val lock = Mutex()
    private var seen = -1L

    suspend fun ask(question: String): TeacherAnswer {
        val asked = question.take(QUESTION_MAX)
        val rules = lock.withLock {
            withContext(Dispatchers.Default) {
                val version = search.version()
                val start = System.nanoTime()
                val cold = version != seen
                if (cold) { teacher.invalidate(); seen = version }
                teacher.ask(asked).also {
                    // times and paths only, never the question or the answer
                    Log.i("JustStudy", "teacher_answer ms=${(System.nanoTime() - start) / 1_000_000} index=$cold path=${it.promptVersion}")
                }
            }
        }
        if (rules.status !in MODEL_STATUSES || gate?.ready() != true) return rules
        return modelAnswer(asked, rules, gate) ?: rules
    }

    private suspend fun modelAnswer(question: String, rules: TeacherAnswer, gate: NanoGate): TeacherAnswer? {
        val sources = rules.sources.take(TEACHER_AI_SOURCES)
        if (sources.isEmpty()) {
            val text = gate.ask(AiRequest(AiUse.Teacher, TEACHER_AI_NO_SOURCES_VERSION, teacherNoSourcesPrompt(question), question, 200, cache = true)) { validNoSources(it) }
                ?: return null
            return TeacherAnswer("answered", text, emptyList(), TEACHER_AI_NO_SOURCES_VERSION, AI_MODEL)
        }
        val request = AiRequest(AiUse.Teacher, TEACHER_AI_VERSION, teacherPrompt(question, sources), question + "\n" + formatSources(sources), 256, cache = true)
        val answer = gate.ask(request) { validTeacher(it, sources) } ?: return null
        return TeacherAnswer("answered", answer.text, sources.map { it.copy(cited = it.n in answer.cited) }, TEACHER_AI_VERSION, AI_MODEL)
    }
}
