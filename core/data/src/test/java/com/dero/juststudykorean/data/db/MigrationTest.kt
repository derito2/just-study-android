package com.dero.juststudykorean.data.db

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dero.juststudykorean.data.backup.SCHEMA
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** v1 → v2 (phase 4) only adds notes_fts and processing_jobs, v2 → v3 and v3 → v4 only indices: every row of the 18 backup tables stays as it was. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MigrationTest {
    private val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "migration-test.db").apply { delete() }

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), file, AndroidSQLiteDriver(), JustStudyDatabase::class)

    private fun dump(c: SQLiteConnection): Map<String, List<List<String?>>> = SCHEMA.keys.associateWith { table ->
        c.prepare("SELECT * FROM \"$table\" ORDER BY 1, 2").use { st ->
            buildList { while (st.step()) add((0 until st.getColumnCount()).map { if (st.isNull(it)) null else st.getText(it) }) }
        }
    }

    @Test
    fun importedDataSurvives() = runBlocking {
        val v1 = helper.createDatabase(1)
        v1.execSQL("INSERT INTO documents VALUES (25, '노트.pdf', 'a.pdf', 'sha', 3, 'ready', 1, 0, 0, 1200, '[]', NULL, 1700000000000, 1700000001000)")
        v1.execSQL("INSERT INTO topics VALUES (1, '-아서/어서', 'grammar', NULL)")
        v1.execSQL("INSERT INTO document_topics VALUES (25, 1, 1, 3)")
        v1.execSQL(
            "INSERT INTO grammar_points VALUES (1, 25, 1, 0, 'pattern', 'others', '-아서/어서', 'because', '[]', " +
                "'[[\"배고파서 먹어요\",\"I eat because I'' m hungry\"]]', '[]', '[]')",
        )
        val before = dump(v1)
        v1.close()

        val v2 = helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2))
        assertEquals(before, dump(v2))
        assertEquals(4, before.values.sumOf { it.size })
        v2.execSQL("INSERT INTO notes_fts (document_id, chroma_id, page, section, topic, content_type, text_hash, text) VALUES (25, '25:1:0', 1, NULL, 'General', 'general', 'h', '배고파서 먹어요')")
        v2.prepare("SELECT COUNT(*) FROM notes_fts WHERE notes_fts MATCH '배고파서'").use { it.step(); assertEquals(1L, it.getLong(0)) }
        v2.close()

        val v3 = helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3)) // also checks the index against schemas/3.json
        assertEquals(before, dump(v3))
        v3.close()

        val v4 = helper.runMigrationsAndValidate(4, listOf(MIGRATION_3_4)) // all of R-002's indices, against schemas/4.json
        assertEquals(before, dump(v4))
        v4.close()
    }
}
