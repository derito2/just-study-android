package com.dero.juststudykorean.data.grammar

import com.dero.juststudykorean.data.db.GrammarDrillAttemptsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.time.AppClock
import com.dero.juststudykorean.korean.coach.noteWords
import com.dero.juststudykorean.korean.grammar.Drill
import com.dero.juststudykorean.korean.grammar.Ending
import com.dero.juststudykorean.korean.grammar.LabVerb
import com.dero.juststudykorean.korean.grammar.drills
import com.dero.juststudykorean.korean.grammar.endingOf
import com.dero.juststudykorean.korean.grammar.wordKindOf
import com.dero.juststudykorean.korean.norm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val SESSION = 10
private val SPOKEN_NOISE = Regex("[\\s\\p{P}]")

/** A grammar point of your notes that Grammar Lab can drill: its ending is known (EndingMatch). [key] = norm(pattern). */
data class LabPoint(val key: String, val pattern: String, val meaning: String, val ending: Ending, val examples: List<Pair<String, String>>)

/** Right if it is one of the answers, ignoring spaces and punctuation ("갈거예요." is "갈 거예요"). */
fun isRight(drill: Drill, given: String): Boolean {
    fun flat(s: String) = s.replace(SPOKEN_NOISE, "")
    return when (drill) {
        is Drill.Choose -> given == drill.answer
        is Drill.Produce -> drill.answers.any { flat(it) == flat(given) }
        is Drill.FillExample -> drill.answers.any { flat(it) == flat(given) }
    }
}

/**
 * F3 (FASE_11_PLAN PR 6): Grammar Lab's points, verbs and attempts. The attempts go to grammar_drill_attempts
 * (phone-only); what you write is never logged.
 */
@Singleton
class GrammarLabRepository @Inject constructor(
    private val db: JustStudyDatabase,
    private val grammar: GrammarRepository,
    private val notes: NotesSource,
    private val clock: AppClock,
) {
    suspend fun points(): List<LabPoint> = grammar.all().mapNotNull { p -> endingOf(p.pattern)?.let { LabPoint(norm(p.pattern), p.pattern, p.meaning, it, p.examples) } }
        .distinctBy { it.key }

    /** The verbs and adjectives of your notes, each with its type when it is sure. */
    suspend fun verbs(): List<LabVerb> = withContext(Dispatchers.IO) {
        val ready = db.documentsDao().all().filter { it.status == "ready" }.sortedBy { it.id }
        noteWords(ready.flatMap(notes::chunks)).filter { it.korean.endsWith("다") }.map { LabVerb(it.korean.trim(), it.english, wordKindOf(it.korean.trim(), it.english)) }
    }

    /** Ten drills of one point (or of all, [pointKey] null), the verbs you missed most first. Same seed, same drills. */
    suspend fun session(pointKey: String?, level: Int, seed: Long): List<Drill> = withContext(Dispatchers.IO) {
        val points = points().filter { pointKey == null || it.key == pointKey }
        val verbs = verbs()
        val misses = db.grammarDrillAttemptsDao().misses().associate { (it.pointKey to it.verb) to it.misses }
        val all = points.flatMapIndexed { i, p -> drills(p.key, p.ending, verbs, p.examples, SESSION, level, seed + i) }
        // In turns between the points, then the most missed first (stable: the order of the turns stays among equals).
        val turns = all.groupBy { it.pointKey }.values.let { groups -> (0 until (groups.maxOfOrNull { it.size } ?: 0)).flatMap { r -> groups.mapNotNull { it.getOrNull(r) } } }
        turns.sortedByDescending { misses[it.pointKey to it.verb] ?: 0 }.take(SESSION)
    }

    suspend fun save(drill: Drill, given: String, correct: Boolean) = withContext(Dispatchers.IO) {
        db.grammarDrillAttemptsDao().insert(
            GrammarDrillAttemptsEntity(db.grammarDrillAttemptsDao().maxId() + 1, drill.pointKey, drill.verb, drill.ending.label, drill::class.simpleName.orEmpty(), given, correct, clock.now().toEpochMilli()),
        )
    }
}
