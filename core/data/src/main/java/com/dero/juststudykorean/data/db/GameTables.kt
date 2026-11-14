package com.dero.juststudykorean.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// Phase 8 (D8-3, FASE_8_PLAN 6): Games' phone-only tables, database v5. Not in the web's models nor in the backup
// (BackupSchema does not list them); keyed by Korean text, so they survive reprocessing and imports.
// In data/db (not data/games as the plan's tree) because the generated JustStudyDatabase names them without imports.

@Entity(tableName = "game_sessions")
data class GameSessionsEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "seed") val seed: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long,
    @ColumnInfo(name = "rounds") val rounds: Long,
    @ColumnInfo(name = "won") val won: Long,
    @ColumnInfo(name = "best_streak") val bestStreak: Long,
)

@Entity(tableName = "game_rounds", indices = [Index("session_id"), Index("created_at")])
data class GameRoundsEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "round_index") val roundIndex: Long,
    @ColumnInfo(name = "game") val game: String,
    @ColumnInfo(name = "level") val level: Long,
    @ColumnInfo(name = "outcome") val outcome: String,
    @ColumnInfo(name = "mistakes") val mistakes: Long,
    @ColumnInfo(name = "response_ms") val responseMs: Long,
    @ColumnInfo(name = "item_keys") val itemKeys: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "game_item_progress")
data class GameItemProgressEntity(
    @PrimaryKey @ColumnInfo(name = "item_key") val itemKey: String,
    @ColumnInfo(name = "seen") val seen: Long,
    @ColumnInfo(name = "correct") val correct: Long,
    @ColumnInfo(name = "wrong") val wrong: Long,
    @ColumnInfo(name = "streak") val streak: Long,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
    @ColumnInfo(name = "last_wrong_at") val lastWrongAt: Long?,
)

@Dao
interface GameSessionsDao {
    @Insert suspend fun insert(row: GameSessionsEntity)
    @Update suspend fun update(row: GameSessionsEntity)
    @Query("SELECT * FROM game_sessions WHERE id = :id") suspend fun get(id: Long): GameSessionsEntity?
    @Query("SELECT COALESCE(MAX(id), 0) FROM game_sessions") suspend fun maxId(): Long
    @Query("SELECT COALESCE(MAX(best_streak), 0) FROM game_sessions") suspend fun bestStreak(): Long
    @Query("SELECT * FROM game_sessions ORDER BY id") suspend fun all(): List<GameSessionsEntity>
}

/** One row of [GameRoundsDao.perGame]. */
data class GameTotals(val game: String, val rounds: Int, val won: Int, val averageResponseMs: Long, val lastPlayedAt: Long)

@Dao
interface GameRoundsDao {
    @Insert suspend fun insert(row: GameRoundsEntity)
    @Query("SELECT COALESCE(MAX(id), 0) FROM game_rounds") suspend fun maxId(): Long
    @Query("SELECT outcome FROM game_rounds WHERE game = :game ORDER BY created_at DESC, id DESC LIMIT :limit")
    suspend fun lastOutcomes(game: String, limit: Int): List<String>
    @Query("SELECT * FROM game_rounds WHERE session_id = :sessionId ORDER BY round_index") suspend fun ofSession(sessionId: Long): List<GameRoundsEntity>
    @Query("SELECT COUNT(*) FROM game_rounds") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM game_rounds WHERE outcome = 'won'") suspend fun won(): Int
    @Query("SELECT COUNT(*) FROM game_rounds WHERE outcome != 'neutral'") suspend fun graded(): Int
    @Query("SELECT COALESCE(AVG(response_ms), 0) FROM game_rounds") suspend fun averageResponseMs(): Long
    @Query("SELECT MAX(created_at) FROM game_rounds") suspend fun lastPlayedAt(): Long?
    @Query(
        "SELECT game, COUNT(*) AS rounds, SUM(CASE WHEN outcome = 'won' THEN 1 ELSE 0 END) AS won, " +
            "CAST(AVG(response_ms) AS INTEGER) AS averageResponseMs, MAX(created_at) AS lastPlayedAt FROM game_rounds GROUP BY game ORDER BY game",
    )
    suspend fun perGame(): List<GameTotals>
}

@Dao
interface GameItemProgressDao {
    @Upsert suspend fun upsert(row: GameItemProgressEntity)
    @Query("SELECT * FROM game_item_progress WHERE item_key = :key") suspend fun get(key: String): GameItemProgressEntity?
    @Query("SELECT * FROM game_item_progress") suspend fun all(): List<GameItemProgressEntity>
    @Query("SELECT item_key FROM game_item_progress WHERE wrong > 0 ORDER BY wrong DESC, last_wrong_at DESC LIMIT :limit")
    suspend fun mostFailed(limit: Int): List<String>
    @Query("SELECT COUNT(*) FROM game_item_progress WHERE streak >= 3") suspend fun mastered(): Int
}

/** v4 → v5 only creates Games' three tables and their indices: no row of the imported data changes (MigrationTest). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `game_sessions` (`id` INTEGER NOT NULL, `seed` INTEGER NOT NULL, `started_at` INTEGER NOT NULL, " +
                "`ended_at` INTEGER, `duration_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, `won` INTEGER NOT NULL, " +
                "`best_streak` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `game_rounds` (`id` INTEGER NOT NULL, `session_id` INTEGER NOT NULL, `round_index` INTEGER NOT NULL, " +
                "`game` TEXT NOT NULL, `level` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `mistakes` INTEGER NOT NULL, `response_ms` INTEGER NOT NULL, " +
                "`item_keys` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_game_rounds_session_id` ON `game_rounds` (`session_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_game_rounds_created_at` ON `game_rounds` (`created_at`)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `game_item_progress` (`item_key` TEXT NOT NULL, `seen` INTEGER NOT NULL, `correct` INTEGER NOT NULL, " +
                "`wrong` INTEGER NOT NULL, `streak` INTEGER NOT NULL, `last_seen_at` INTEGER NOT NULL, `last_wrong_at` INTEGER, PRIMARY KEY(`item_key`))",
        )
    }
}
