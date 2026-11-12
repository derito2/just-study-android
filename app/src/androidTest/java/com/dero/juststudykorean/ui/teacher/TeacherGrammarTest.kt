package com.dero.juststudykorean.ui.teacher

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dero.juststudykorean.MainActivity
import com.dero.juststudykorean.data.backup.BackupFormat
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.korean.chunkDocument
import com.dero.juststudykorean.korean.teacher.MemoryStore
import com.dero.juststudykorean.korean.teacher.Teacher
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On the OPPO: a grammar question in Korean, typed straight into Teacher's field (no keyboard, no settings), about a rule
 * of YOUR notes, is answered from them with a source. Only reads: Teacher writes nothing. Never prints the question or
 * the answer (on failure, only which check failed).
 */
@RunWith(AndroidJUnit4::class)
class TeacherGrammarTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    /** The first grammar point of your notes, from the app's extracted pages (read only). */
    private fun pattern(): String? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notes = NotesSource(context)
        val ids = File(context.filesDir, "${BackupFormat.FILES_DIR}/extracted").listFiles().orEmpty().mapNotNull { it.nameWithoutExtension.toLongOrNull() }.sorted()
        val chunks = ids.flatMap { id -> notes.pages(id)?.let { chunkDocument(id, "notes", it) }.orEmpty() }
        return Teacher(MemoryStore(chunks)).index().points.map { it.second.pattern }.firstOrNull { p -> p.any { it in '가'..'힣' } }
    }

    private fun shown(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun grammarQuestionInKoreanIsAnsweredFromYourNotes() {
        val pattern = pattern()
        assumeTrue("no grammar point in the notes", pattern != null)
        rule.onNode(isSelectable() and hasText("Teacher")).performClick()
        rule.onNode(hasSetTextAction()).performTextInput("What does $pattern mean?")
        rule.onNodeWithText("SEND").performClick()
        val refusals = listOf("Your notes don't cover this", "Nothing close enough in your notes", "Teacher couldn't answer that.")
        rule.waitUntil(15_000) { shown("Sources from your notes") || refusals.any(::shown) }
        check(refusals.none(::shown)) { "refused" }
        check(shown("Sources from your notes")) { "no sources" }
        check(shown(" = ") && shown("[1]")) { "the answer doesn't cite your notes" }
    }
}
