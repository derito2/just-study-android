package com.dero.juststudykorean.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// Phase 11 (FASE_11_PLAN PR 3; PLAN_FUNCIONES_ESTUDIO F3 and F4): the labs' attempts, database v7. Phone-only, like Games
// (D8-3): not in the backup. No session_id and no practice_sessions: time in BETA doesn't count as studying (D11-2).

/** One Grammar Lab answer. [given] is what you wrote (never your notes, never logged); [pointKey] = norm(the point's pattern). */
@Entity(tableName = "grammar_drill_attempts", indices = [Index("point_key"), Index("created_at")])
data class GrammarDrillAttemptsEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "point_key") val pointKey: String,
    @ColumnInfo(name = "verb") val verb: String,
    @ColumnInfo(name = "ending") val ending: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "given") val given: String,
    @ColumnInfo(name = "correct") val correct: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** One Listening Lab item. Only whether it matched: never the audio nor what was said (D9-1, D11-6). */
@Entity(tableName = "listening_attempts", indices = [Index("item_key"), Index("created_at")])
data class ListeningAttemptsEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "item_key") val itemKey: String,
    @ColumnInfo(name = "correct") val correct: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** How often a verb was missed in a point (Grammar Lab puts those first). */
data class DrillMisses(val pointKey: String, val verb: String, val misses: Int)

@Dao
interface GrammarDrillAttemptsDao {
    @Insert suspend fun insert(row: GrammarDrillAttemptsEntity)
    @Query("SELECT COALESCE(MAX(id), 0) FROM grammar_drill_attempts") suspend fun maxId(): Long
    @Query("SELECT COUNT(*) FROM grammar_drill_attempts") suspend fun count(): Int
    @Query("SELECT point_key AS pointKey, verb, COUNT(*) AS misses FROM grammar_drill_attempts WHERE correct = 0 GROUP BY point_key, verb")
    suspend fun misses(): List<DrillMisses>
}

@Dao
interface ListeningAttemptsDao {
    @Insert suspend fun insert(row: ListeningAttemptsEntity)
}

/** v6 → v7 only creates the two tables and their indices, empty. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `grammar_drill_attempts` (`id` INTEGER NOT NULL, `point_key` TEXT NOT NULL, `verb` TEXT NOT NULL, " +
                "`ending` TEXT NOT NULL, `kind` TEXT NOT NULL, `given` TEXT NOT NULL, `correct` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_grammar_drill_attempts_point_key` ON `grammar_drill_attempts` (`point_key`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_grammar_drill_attempts_created_at` ON `grammar_drill_attempts` (`created_at`)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `listening_attempts` (`id` INTEGER NOT NULL, `mode` TEXT NOT NULL, `item_key` TEXT NOT NULL, " +
                "`correct` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_attempts_item_key` ON `listening_attempts` (`item_key`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_attempts_created_at` ON `listening_attempts` (`created_at`)")
    }
}
