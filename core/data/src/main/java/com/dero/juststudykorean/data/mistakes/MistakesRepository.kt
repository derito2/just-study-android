package com.dero.juststudykorean.data.mistakes

import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.korean.grammar.Ending
import com.dero.juststudykorean.korean.grammar.conjugate
import com.dero.juststudykorean.korean.mistakes.Mistake
import com.dero.juststudykorean.korean.norm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

// F5 (FASE_11_PLAN PR 9): your mistakes from Quiz, Flashcards (Again), Games, Grammar Lab and the dictation. Read only.

private val HANGUL = Regex("[가-힣]")

@Singleton
class MistakesRepository @Inject constructor(private val db: JustStudyDatabase) {
    /** Every mistake since [since] (epoch ms); [Mistake.wordKey] is word_state's key (norm of the Korean) when there is one. */
    suspend fun mistakes(since: Long): List<Mistake> = withContext(Dispatchers.IO) {
        val hot = db.hotDao()
        fun word(text: String?) = text?.takeIf { HANGUL.containsMatchIn(it) }?.let(::norm)
        hot.quizMistakes(since).map { Mistake("Quiz", it.expected, it.given, word(it.ref), null, it.at) } +
            hot.againMistakes(since).map { Mistake("Flashcards", it.expected, null, word(it.ref), null, it.at) } +
            hot.gameMistakes(since).flatMap { r ->
                val keys = runCatching { JSONArray(r.expected).let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(emptyList())
                keys.map { k -> Mistake("Games", k.substringAfter(':'), null, k.takeIf { it.startsWith("w:") }?.drop(2), k.takeIf { it.startsWith("g:") }?.drop(2), r.at) }
            } +
            hot.drillMistakes(since).mapNotNull { r ->
                val (verb, label) = r.expected.split('|', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                val ending = Ending.entries.firstOrNull { it.label == label } ?: return@mapNotNull null
                Mistake("Grammar Lab", conjugate(verb, ending).firstOrNull() ?: return@mapNotNull null, r.given, word(verb), r.ref, r.at)
            } +
            hot.dictationMistakes(since).map { Mistake("Listening", it.expected.substringAfter(':'), null, it.ref?.takeIf { k -> k.startsWith("w:") }?.drop(2), null, it.at) }
    }
}
