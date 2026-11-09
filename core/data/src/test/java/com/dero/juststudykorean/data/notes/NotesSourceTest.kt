package com.dero.juststudykorean.data.notes

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.backup.BackupFormat
import com.dero.juststudykorean.data.db.DocumentsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** The chunk cache: same list while the extracted file stays, new chunks once it is rewritten. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class NotesSourceTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val doc = DocumentsEntity(7, "notes.pdf", "s", "x", 1, "ready", true, 0, 0, 0, null, null, 0, null)
    private val file = File(context.filesDir, "${BackupFormat.FILES_DIR}/${BackupFormat.extractedEntry("7")}").apply { parentFile!!.mkdirs() }

    private fun write(text: String) = file.writeText("""{"pages":[{"page_number":1,"section":null,"text":"$text","tables":[]}]}""")

    @Test fun cachedUntilTheFileChanges() {
        val notes = NotesSource(context)
        write("사과 apple, 바나나 banana, 포도 grape")
        val first = notes.chunks(doc)
        assertSame(first, notes.chunks(doc))
        write("학교 school, 선생님 teacher, 학생 student, 교실 classroom")
        file.setLastModified(file.lastModified() + 1_000)
        assertEquals("학교", notes.chunks(doc).single().text.take(2))
    }
}
