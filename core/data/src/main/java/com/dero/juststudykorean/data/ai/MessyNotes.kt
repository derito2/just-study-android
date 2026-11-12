package com.dero.juststudykorean.data.ai

import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import com.dero.juststudykorean.data.documents.DocumentIndexer
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.ai.AiRequest
import com.dero.juststudykorean.ai.AiUse
import com.dero.juststudykorean.ai.NanoGate
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.korean.VocabPair
import com.dero.juststudykorean.korean.ai.PAIRS_VERSION
import com.dero.juststudykorean.korean.ai.needsLlmPairs
import com.dero.juststudykorean.korean.ai.pairsPrompt
import com.dero.juststudykorean.korean.ai.parsePairs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A document's messy notes: [waiting] while the app is in the background; [pairs] the model found (AiMark). */
data class MessyNotesState(val waiting: Boolean = false, val done: Boolean = false, val pairs: List<VocabPair> = emptyList(), val pages: Map<String, Int> = emptyMap())

/**
 * Phase 6, task 5 (use 6): pairs "Korean = English" from the fragments the rules don't understand (needs_llm_pairs),
 * checked like the web (both sides close together in the fragment). It runs after a document is ready (the gate never
 * calls the model with a PDF in the queue) and only with the app in front: in the background it waits, and the Library
 * starts it again when you come back. The pairs live in memory only: no cards, no backup table.
 */
@Singleton
class MessyNotes @Inject constructor(
    private val gate: NanoGate,
    private val db: JustStudyDatabase,
    private val notes: NotesSource,
    private val files: DocumentFiles,
    private val indexer: DocumentIndexer,
) {
    private val _state = MutableStateFlow<Map<Long, MessyNotesState>>(emptyMap())
    val state: StateFlow<Map<Long, MessyNotesState>> = _state.asStateFlow()
    private val lock = Mutex()

    /** Reads the messy notes of every ready document not read yet (one call per fragment, through the gate). */
    suspend fun readReady() = lock.withLock {
        if (!gate.ready()) return@withLock
        val docs = withContext(Dispatchers.IO) { db.documentsDao().all().filter { it.status == "ready" } }
        for (doc in docs) {
            if (_state.value[doc.id]?.done == true) continue
            val messy = withContext(Dispatchers.Default) { notes.chunks(doc).filter(::needsLlmPairs) }
            if (messy.isEmpty()) { set(doc.id, MessyNotesState(done = true)); continue }
            val pairs = _state.value[doc.id]?.pairs.orEmpty().toMutableList()
            val pages = _state.value[doc.id]?.pages.orEmpty().toMutableMap()
            for (chunk in messy) {
                val found = gate.ask(AiRequest(AiUse.MessyNotes, PAIRS_VERSION, pairsPrompt(chunk), chunk.text, 256, 0f, cache = true)) { out ->
                    parsePairs(out, chunk)
                }
                // Not now (in the background, a PDF in the queue, no model): try again later, from where it stopped.
                if (found == null && gate.lastResult in setOf("background", "pdf_queued", "unavailable")) {
                    return@withLock set(doc.id, MessyNotesState(waiting = gate.lastResult == "background", pairs = pairs, pages = pages))
                }
                found?.forEach { p -> if (pairs.none { it.korean == p.korean }) { pairs += p; pages[p.korean] = chunk.pageNumber } }
            }
            set(doc.id, MessyNotesState(done = true, pairs = pairs, pages = pages))
        }
    }

    private fun set(id: Long, s: MessyNotesState) = _state.update { it + (id to s) }

    /**
     * Add (design/library.md E): each pair becomes a normal word of the document, a "Korean = English" line at the end of
     * its page in extracted/<id>.json (the backup's format doesn't change), and the notes index is rebuilt. Returns what
     * was added (for Undo).
     */
    suspend fun accept(documentId: Long, chosen: List<VocabPair>): List<VocabPair> = lock.withLock {
        val s = _state.value[documentId] ?: return@withLock emptyList()
        val adding = chosen.filter { c -> s.pairs.any { it.korean == c.korean } && s.pages[c.korean] != null }
        if (adding.isEmpty()) return@withLock emptyList()
        rewrite(documentId) { page, text -> text + adding.filter { s.pages[it.korean] == page }.joinToString("") { "\n${line(it)}" } }
        set(documentId, s.copy(pairs = s.pairs.filter { p -> adding.none { it.korean == p.korean } }))
        adding
    }

    /** Undo: takes those lines out again and shows the pairs as the model's. */
    suspend fun undo(documentId: Long, added: List<VocabPair>) = lock.withLock {
        val s = _state.value[documentId] ?: return@withLock
        val lines = added.map(::line).toSet()
        rewrite(documentId) { _, text -> text.lines().filter { it !in lines }.joinToString("\n") }
        set(documentId, s.copy(pairs = s.pairs + added.filter { a -> s.pairs.none { it.korean == a.korean } }))
    }

    private fun line(p: VocabPair) = "${p.korean} = ${p.english}"

    private suspend fun rewrite(documentId: Long, edit: (page: Int, text: String) -> String) = withContext(Dispatchers.IO) {
        val json = Json.parseToJsonElement(files.extracted(documentId).readText()).jsonObject
        val pages = json.getValue("pages").jsonArray.map { p ->
            val o = p.jsonObject
            val text = o["text"]?.jsonPrimitive?.contentOrNull ?: ""
            JsonObject(o + ("text" to JsonPrimitive(edit(o.getValue("page_number").jsonPrimitive.int, text))))
        }
        files.writeExtracted(documentId, JsonObject(json + ("pages" to JsonArray(pages))).toString())
        indexer.index(documentId)
    }
}
