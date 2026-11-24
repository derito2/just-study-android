package com.dero.juststudykorean.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// Phase 11 (H-03 phase B, FASE_11_PLAN PR 2): one row per word, database v6. Phone-only, like Games (D8-3): not in the
// backup (BackupSchema does not list them). A cache: the truth stays in flashcards, Games and Quiz, and
// WordProgress.rebuildWordState() can always write it again from them.

/** [hard] and [masteredAt] are D11-1 (WordRules) over the sources; times are null for a word never studied. */
@Entity(tableName = "word_state")
data class WordStateEntity(
    @PrimaryKey @ColumnInfo(name = "word_key") val wordKey: String,
    @ColumnInfo(name = "first_seen_at") val firstSeenAt: Long?,
    @ColumnInfo(name = "last_event_at") val lastEventAt: Long?,
    @ColumnInfo(name = "again_count") val againCount: Long,
    @ColumnInfo(name = "wrong_count") val wrongCount: Long,
    @ColumnInfo(name = "correct_count") val correctCount: Long,
    @ColumnInfo(name = "source_mask") val sourceMask: Long,
    @ColumnInfo(name = "mastered_at") val masteredAt: Long?,
    @ColumnInfo(name = "hard", defaultValue = "0") val hard: Boolean,
)

/** The word a quiz question is about (its answer or, else, its prompt is that word). */
@Entity(tableName = "question_words", primaryKeys = ["question_id", "word_key"], indices = [Index("word_key")])
data class QuestionWordsEntity(
    @ColumnInfo(name = "question_id") val questionId: Long,
    @ColumnInfo(name = "word_key") val wordKey: String,
)

@Dao
interface WordStateDao {
    @Upsert suspend fun upsert(rows: List<WordStateEntity>)
    @Query("SELECT * FROM word_state") suspend fun all(): List<WordStateEntity>
    @Query("SELECT word_key FROM word_state WHERE word_key IN (:keys)") suspend fun existing(keys: List<String>): List<String>
    @Query("DELETE FROM word_state") suspend fun deleteAll()

    /** A few cards' reviews (the rest of WordProgress reads them all). */
    @Query("SELECT * FROM flashcard_reviews WHERE flashcard_id IN (:cardIds)") suspend fun reviewsOf(cardIds: List<Long>): List<FlashcardReviewsEntity>
}

@Dao
interface QuestionWordsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(rows: List<QuestionWordsEntity>)
    @Query("SELECT * FROM question_words") suspend fun all(): List<QuestionWordsEntity>
    @Query("SELECT * FROM question_words WHERE word_key IN (:keys)") suspend fun ofWords(keys: List<String>): List<QuestionWordsEntity>
    @Query("SELECT word_key FROM question_words WHERE question_id = :questionId") suspend fun wordsOf(questionId: Long): List<String>
    @Query("DELETE FROM question_words") suspend fun deleteAll()
}

/** v5 → v6 only creates the two tables and the index, empty: WordProgress fills them from the sources afterwards. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `word_state` (`word_key` TEXT NOT NULL, `first_seen_at` INTEGER, `last_event_at` INTEGER, " +
                "`again_count` INTEGER NOT NULL, `wrong_count` INTEGER NOT NULL, `correct_count` INTEGER NOT NULL, `source_mask` INTEGER NOT NULL, " +
                "`mastered_at` INTEGER, `hard` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`word_key`))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `question_words` (`question_id` INTEGER NOT NULL, `word_key` TEXT NOT NULL, PRIMARY KEY(`question_id`, `word_key`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_question_words_word_key` ON `question_words` (`word_key`)")
    }
}
