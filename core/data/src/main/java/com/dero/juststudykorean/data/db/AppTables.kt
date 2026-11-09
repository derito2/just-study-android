package com.dero.juststudykorean.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Fts4
import androidx.room3.FtsOptions
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow

// Phone-only tables (database v2, phase 4). They are not part of the web's models nor of the backup: the notes index
// is rebuilt from extracted/ and the pipeline state only lives while a PDF is processed.

/**
 * Full-text index of your notes' chunks (the web's chunk_index + the text Chroma held), for Teacher's search in phase 5.
 * Only `text` is searchable; the rest are stored as they are.
 */
@Fts4(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    notIndexed = ["document_id", "chroma_id", "page", "section", "topic", "content_type", "text_hash"],
)
@Entity(tableName = "notes_fts")
data class NotesFtsEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Long = 0,
    @ColumnInfo(name = "document_id") val documentId: Long,
    @ColumnInfo(name = "chroma_id") val chromaId: String,
    @ColumnInfo(name = "page") val page: Long,
    @ColumnInfo(name = "section") val section: String?,
    @ColumnInfo(name = "topic") val topic: String,
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "text_hash") val textHash: String,
    @ColumnInfo(name = "text") val text: String,
)

@Dao
interface NotesFtsDao {
    @Insert suspend fun insertAll(rows: List<NotesFtsEntity>)

    @Query("SELECT * FROM notes_fts WHERE document_id = :documentId")
    suspend fun forDocument(documentId: Long): List<NotesFtsEntity>

    @Query("SELECT text_hash FROM notes_fts WHERE document_id < :documentId")
    suspend fun hashesBefore(documentId: Long): List<String>

    @Query("DELETE FROM notes_fts WHERE document_id = :documentId")
    suspend fun deleteDocument(documentId: Long)

    @Query("DELETE FROM notes_fts")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM notes_fts")
    suspend fun count(): Int

    /** indexing_service.delete_orphan_topics: topics nothing points to (never one with study history). */
    @Query(
        "DELETE FROM topics WHERE id NOT IN (SELECT topic_id FROM document_topics) " +
            "AND id NOT IN (SELECT topic_id FROM questions WHERE topic_id IS NOT NULL) " +
            "AND id NOT IN (SELECT topic_id FROM flashcards WHERE topic_id IS NOT NULL) " +
            "AND id NOT IN (SELECT topic_id FROM user_topic_progress) " +
            "AND id NOT IN (SELECT topic_id FROM study_session_topics) " +
            "AND id NOT IN (SELECT topic_id FROM vocabulary WHERE topic_id IS NOT NULL) " +
            "AND id NOT IN (SELECT topic_id FROM examples WHERE topic_id IS NOT NULL) " +
            "AND id NOT IN (SELECT topic_id FROM ml_topic_scores)",
    )
    suspend fun deleteOrphanTopics(): Int
}

/** Where a PDF is in the pipeline (PENDING → EXTRACTING → PARSING → CHUNKING → INDEXING); gone when it ends. */
@Entity(tableName = "processing_jobs", primaryKeys = ["document_id"])
data class ProcessingJobsEntity(
    @ColumnInfo(name = "document_id") val documentId: Long,
    @ColumnInfo(name = "stage") val stage: String,
    @ColumnInfo(name = "queued_at") val queuedAt: Long,
)

@Dao
interface ProcessingJobsDao {
    @Query("SELECT * FROM processing_jobs ORDER BY queued_at, document_id")
    suspend fun all(): List<ProcessingJobsEntity>

    @Query("SELECT * FROM processing_jobs ORDER BY queued_at, document_id")
    fun observe(): Flow<List<ProcessingJobsEntity>>

    @Query("SELECT * FROM processing_jobs WHERE document_id = :documentId")
    suspend fun get(documentId: Long): ProcessingJobsEntity?

    @Upsert suspend fun upsert(row: ProcessingJobsEntity)

    @Query("DELETE FROM processing_jobs WHERE document_id = :documentId")
    suspend fun delete(documentId: Long)
}

/** v1 → v2 adds only the two phone-only tables: the imported data is not touched (MigrationTest). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` USING FTS4(`document_id` INTEGER NOT NULL, `chroma_id` TEXT NOT NULL, " +
                "`page` INTEGER NOT NULL, `section` TEXT, `topic` TEXT NOT NULL, `content_type` TEXT NOT NULL, `text_hash` TEXT NOT NULL, " +
                "`text` TEXT NOT NULL, tokenize=unicode61, notindexed=`document_id`, notindexed=`chroma_id`, notindexed=`page`, " +
                "notindexed=`section`, notindexed=`topic`, notindexed=`content_type`, notindexed=`text_hash`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `processing_jobs` (`document_id` INTEGER NOT NULL, `stage` TEXT NOT NULL, " +
                "`queued_at` INTEGER NOT NULL, PRIMARY KEY(`document_id`))",
        )
    }
}
