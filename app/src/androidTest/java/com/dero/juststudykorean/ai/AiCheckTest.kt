package com.dero.juststudykorean.ai

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dero.juststudykorean.MainActivity
import com.dero.juststudykorean.data.ai.CoachAi
import com.dero.juststudykorean.data.ai.GrammarAi
import com.dero.juststudykorean.data.ai.MessyNotes
import com.dero.juststudykorean.data.ai.QuizAi
import com.dero.juststudykorean.data.backup.BackupFormat
import com.dero.juststudykorean.data.coach.CoachEngine
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.ProcessingJobsEntity
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.grammar.GrammarFilter
import com.dero.juststudykorean.data.grammar.GrammarRepository
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.quiz.QuizRepository
import com.dero.juststudykorean.data.quiz.QuizRequest
import com.dero.juststudykorean.data.teacher.TeacherService
import com.dero.juststudykorean.korean.ai.AI_MODEL
import com.dero.juststudykorean.korean.ai.inventedKorean
import com.dero.juststudykorean.korean.teacher.formatSources
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase 6, task 7 on the OPPO: the 8 uses through the gate on a COPY of your database (files/ai-check/juststudy.db, put
 * there with run-as; your app's database is never opened for writing). Writes only figures to files/ai-check/result.txt:
 * per use, calls, p50/p90 and the gate's results (ok, rejected, timeout…); never a prompt, an answer or your notes.
 */
@RunWith(AndroidJUnit4::class)
class AiCheckTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.filesDir, "ai-check")
    private val times = LinkedHashMap<String, MutableList<Long>>()
    private val results = LinkedHashMap<String, MutableMap<String, Int>>()

    private suspend fun <T> measure(use: String, gate: NanoGate, block: suspend () -> T): T {
        val t0 = SystemClock.elapsedRealtime()
        val out = block()
        times.getOrPut(use) { mutableListOf() } += SystemClock.elapsedRealtime() - t0
        results.getOrPut(use) { linkedMapOf() }.merge(gate.lastResult.ifEmpty { "rules" }, 1, Int::plus)
        return out
    }

    private fun pct(xs: List<Long>, p: Int) = xs.sorted()[((xs.size - 1) * p) / 100]

    @Test
    fun theEightUsesOnACopy() = runBlocking {
        assumeTrue("put a copy of the database in files/ai-check first", File(dir, "juststudy.db").isFile)
        val db = Room.databaseBuilder(context, JustStudyDatabase::class.java, File(dir, "juststudy.db").path).setDriver(BundledSQLiteDriver()).build()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            try {
                val model = MlKitNano()
                val state = NanoState(model).also { it.refresh() }
                val until = SystemClock.elapsedRealtime() + 10_000
                while (state.status.value != NanoStatus.Available && SystemClock.elapsedRealtime() < until) delay(200)
                assumeTrue("Nano is not Available", state.status.value == NanoStatus.Available)
                val gate = NanoGate(model, state) { db.processingJobsDao().all().isNotEmpty() }
                val clock = com.dero.juststudykorean.data.time.SystemClock()
                val notes = NotesSource(context) // your extracted pages: read only
                val progress = ProgressRepository(db, clock)
                val quiz = QuizRepository(db, clock, progress, notes)
                val quizAi = QuizAi(gate, db, notes)
                var koreanApproved = 0
                var inventedShown = 0

                // Uses 1 and 3: the judge, on typed questions of a quiz made on the copy. Never Korean.
                val run = quiz.generate(QuizRequest(count = 5, types = listOf("ko_to_en"), seed = 7))
                for (q in run?.questions.orEmpty()) {
                    measure("1-3 judge", gate) { quizAi.sameMeaning(q.prompt, q.correctAnswer, "the " + q.correctAnswer.lowercase()) }
                    if (quizAi.sameMeaning(q.prompt, q.correctAnswer, "가나다")) koreanApproved++
                }
                // Use 2: explain a mistake (the question's own explanation is the grammar point).
                val withPoint = db.questionsDao().all().filter { !it.explanation.isNullOrBlank() && it.optionsJson != null }.take(4)
                for (q in withPoint) {
                    val wrong = Regex("\"([^\"]+)\"").findAll(q.optionsJson!!).map { it.groupValues[1] }.firstOrNull { it != q.correctAnswer } ?: continue
                    val text = measure("2 mistake", gate) { quizAi.explainMistake(q.prompt, q.correctAnswer, wrong, q.explanation) }
                    if (text != null && inventedKorean(text, "${q.prompt}\n${q.correctAnswer}\n$wrong\n${q.explanation}").isNotEmpty()) inventedShown++
                }
                // The model's questions (memory only).
                measure("quiz questions", gate) { quizAi.questions(QuizRequest(count = 3, types = listOf("multiple_choice", "short_answer")), listOf(1, 2), 7) }
                // Use 4: Teacher where the rules have no answer.
                val teacher = TeacherService(db, gate)
                for (q in listOf("Why do Koreans use honorifics?", "How do I make a polite request?", "What is the difference between 은 and 는?", "How do I count in Korean?")) {
                    val a = measure("4 teacher", gate) { teacher.ask(q) }
                    if (a.model == AI_MODEL) {
                        val cited = a.sources.filter { it.cited }
                        if (inventedKorean(a.answer, if (cited.isEmpty()) "" else formatSources(cited)).isNotEmpty()) inventedShown++
                    }
                }
                // Use 5: the coach's text from the rules' facts.
                val coach = CoachEngine(db, clock, progress, FlashcardRepository(db, clock, progress, notes), notes)
                val coachAi = CoachAi(gate, db)
                for (q in listOf("What should I study?", "Quiz on my mistakes", "How am I doing?")) measure("5 coach", gate) { coachAi.write(coach.ask(q)) }
                // Use 6: messy notes, on a synthetic document that exists only in the copy (its pages in files/ai-check).
                val sandbox = object : ContextWrapper(context) { override fun getFilesDir() = File(dir, "files") }
                File(sandbox.filesDir, "${BackupFormat.FILES_DIR}/${BackupFormat.extractedEntry("990001")}").apply { parentFile!!.mkdirs() }
                    .writeText("""{"pages": [{"page_number": 1, "section": "Vocabulary", "text": "Meaning: the words below\n사과\napple\n우유\nmilk", "tables": []}]}""")
                db.documentsDao().insert(DocumentsEntity(990001, "messy.pdf", "messy.pdf", "sha", 1, "ready", true, 0, 0, 0, "[]", null, 0, 0))
                val sandboxNotes = NotesSource(sandbox as Context)
                val sandboxFiles = com.dero.juststudykorean.data.documents.DocumentFiles(sandbox)
                val messy = MessyNotes(gate, db, sandboxNotes, sandboxFiles, com.dero.juststudykorean.data.documents.DocumentIndexer(db, clock, progress, sandboxNotes, sandboxFiles))
                measure("6 messy notes", gate) { messy.readReady() }
                // Uses 7 and 8.
                val grammarAi = GrammarAi(gate, db, notes)
                for (p in GrammarRepository(db).points(GrammarFilter.All, "").first().take(3)) {
                    val s = measure("7a simpler", gate) { grammarAi.simpler(p) }
                    if (s != null && inventedKorean(s, (listOf(p.pattern, p.meaning) + p.structure + p.explanation + p.examples.map { "${it.first} ${it.second}" } + p.notes).joinToString("\n")).isNotEmpty()) inventedShown++
                    measure("7b practice", gate) { grammarAi.practice(p) }
                }
                measure("8 dashboard", gate) { grammarAi.week(progress.weekly(), progress.recommended(1).firstOrNull()?.name) }

                // Leaving the app with a call in progress: no crash, and the next call works again.
                val pending = async { runCatching { quizAi.explainMistake("Pick one: 책___", "이", "가", "이/가 = subject marker") } }
                delay(300)
                scenario.moveToState(Lifecycle.State.CREATED)
                val left = pending.await()
                scenario.moveToState(Lifecycle.State.RESUMED)
                val afterLeaving = "left=${if (left.isSuccess) "no crash (${gate.lastResult})" else "threw ${left.exceptionOrNull()?.javaClass?.simpleName}"}"
                // A PDF in the queue: the gate says no without calling.
                db.processingJobsDao().upsert(ProcessingJobsEntity(990001, "pending", System.currentTimeMillis()))
                val queued = gate.ask(AiRequest(AiUse.Coach, "check", "Say hi.", "queued", 8)) { it }
                val queuedResult = gate.lastResult
                db.processingJobsDao().delete(990001)

                val lines = times.map { (use, ts) -> "$use calls=${ts.size} p50=${pct(ts, 50)}ms p90=${pct(ts, 90)}ms results=${results[use]}" } +
                    listOf(afterLeaving, "pdf_queued=${queued == null && queuedResult == "pdf_queued"}", "judge_approved_korean=$koreanApproved", "invented_korean_shown=$inventedShown")
                File(dir, "result.txt").writeText(lines.joinToString("\n") + "\n")
                assertEquals("the judge approved Korean", 0, koreanApproved)
                assertEquals("invented Korean reached the screen", 0, inventedShown)
                assertFalse("a PDF in the queue let the model run", queued != null)
            } finally {
                db.close()
            }
        }
    }
}
