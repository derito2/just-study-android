package com.dero.juststudykorean.data.games

import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.korean.games.GameBank
import com.dero.juststudykorean.korean.games.buildGameBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Where a session gets its bank (a fake in tests). */
fun interface BankSource {
    suspend fun bank(): GameBank
}

/**
 * FASE_8_PLAN 4.1: the bank of your ready documents, built once in Dispatchers.Default and kept in memory; built again
 * when the documents or their extracted/ files change (the same footprint NotesSource uses: date and size of each).
 */
@Singleton
class GameContentProvider @Inject constructor(
    private val db: JustStudyDatabase,
    private val notes: NotesSource,
    private val files: DocumentFiles,
) : BankSource {
    private val lock = Mutex()
    private var cached: Pair<List<Any>, GameBank>? = null

    override suspend fun bank(): GameBank = lock.withLock {
        withContext(Dispatchers.Default) {
            val ready = db.documentsDao().all().filter { it.status == "ready" }.sortedBy { it.id }
            val footprint = ready.flatMap { d -> files.extracted(d.id).let { f -> listOf(d.id, d.originalName, f.lastModified(), f.length()) } }
            cached?.takeIf { it.first == footprint }?.second ?: buildGameBank(ready.flatMap(notes::chunks)).also { cached = footprint to it }
        }
    }
}
