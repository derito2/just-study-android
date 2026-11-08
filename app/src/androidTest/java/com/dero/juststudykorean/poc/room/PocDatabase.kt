package com.dero.juststudykorean.poc.room

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// PoC code (branch poc/3-room only): one entity per backup table, columns 1:1, dates as Long
// (epoch millis, UTC). Rows go in and out through SQL built from SCHEMA, not DAOs.
@Database(
    entities = [
        CustomDecksRow::class, DeckGroupsRow::class, DocumentsRow::class, QuizRunsRow::class,
        StudySessionsRow::class, TopicsRow::class, DeckPlacesRow::class, DocumentTopicsRow::class,
        GrammarPointsRow::class, MlTopicScoresRow::class, QuestionsRow::class,
        StudySessionTopicsRow::class, UserTopicProgressRow::class, VocabularyRow::class,
        ExamplesRow::class, FlashcardsRow::class, QuestionAttemptsRow::class, FlashcardReviewsRow::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PocDatabase : RoomDatabase() {
    companion object {
        fun open(context: Context, name: String): PocDatabase {
            context.deleteDatabase(name)
            return Room.databaseBuilder(context, PocDatabase::class.java, name)
                .setDriver(BundledSQLiteDriver())
                .build()
        }
    }
}
