package com.dero.juststudykorean.data.quiz

import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.TopicsEntity
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.korean.Chunk
import com.dero.juststudykorean.korean.cleanTopic

/** The topic of a chunk's questions and cards (quiz_service.topic_for): the section, or the document's name for "General". */
internal class Topics(private val db: JustStudyDatabase, private val progress: ProgressRepository) {
    private val cache = HashMap<String, Long>()

    suspend fun idFor(chunk: Chunk): Long {
        val name = if (chunk.topic != "General") chunk.topic else cleanTopic(chunk.filename.substringBeforeLast("."))
        val key = name.lowercase()
        return cache.getOrPut(key) {
            db.topicsDao().all().sortedBy { it.id }.firstOrNull { it.name.lowercase() == key }?.id
                ?: TopicsEntity(progress.nextId("topics"), name, if (chunk.contentType in setOf("grammar", "vocabulary")) chunk.contentType else "other", null)
                    .also { db.topicsDao().insert(it) }.id
        }
    }
}
