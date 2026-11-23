package com.dero.juststudykorean.data.ai

import com.dero.juststudykorean.ai.AiRequest
import com.dero.juststudykorean.ai.AiUse
import com.dero.juststudykorean.ai.NanoGate
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.grammar.GrammarPointUi
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.korean.ai.PRACTICE_VERSION
import com.dero.juststudykorean.korean.ai.SIMPLER_VERSION
import com.dero.juststudykorean.korean.ai.practicePrompt
import com.dero.juststudykorean.korean.ai.simplerPrompt
import com.dero.juststudykorean.korean.ai.validPractice
import com.dero.juststudykorean.korean.ai.validSimpler
import com.dero.juststudykorean.korean.extractPairs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 6, task 6: Grammar (use 7), through [NanoGate] (use 8, the Dashboard's week, was removed: design/ajustes.md AJ-10). Nothing is saved: the practice sentence
 * is always labelled PRACTICE and never becomes yours.
 */
@Singleton
class GrammarAi @Inject constructor(private val gate: NanoGate, private val db: JustStudyDatabase, private val notes: NotesSource) {
    fun ready() = gate.ready()

    private fun text(p: GrammarPointUi) = (listOf("${p.pattern} = ${p.meaning}") + p.structure + p.explanation + p.examples.map { (k, e) -> "$k = $e" } + p.notes).joinToString("\n")

    /** 7a: a simpler explanation; null → your notes' explanation (already on the card). */
    suspend fun simpler(p: GrammarPointUi): String? {
        val point = text(p)
        return gate.ask(AiRequest(AiUse.Grammar, SIMPLER_VERSION, simplerPrompt(point), point, 200, cache = true)) { validSimpler(it, point) }
    }

    /** 7b: a practice sentence with the pattern and only words of that document's notes; null → your examples. */
    suspend fun practice(p: GrammarPointUi): String? {
        val pairs = withContext(Dispatchers.Default) {
            val doc = db.documentsDao().all().firstOrNull { it.id == p.source.documentId } ?: return@withContext emptyList()
            notes.chunks(doc).flatMap(::extractPairs).distinctBy { it.korean }
        }
        if (pairs.isEmpty()) return null
        val words = pairs.take(30)
        val allowed = text(p) + "\n" + words.joinToString("\n") { "${it.korean} = ${it.english}" }
        return gate.ask(AiRequest(AiUse.Grammar, PRACTICE_VERSION, practicePrompt(p.pattern, words.map { it.korean }), allowed, 96, 0.7f)) {
            validPractice(it, p.pattern, allowed)
        }
    }

}
