package com.dero.juststudykorean.ui.screens

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ai.NanoModel
import com.dero.juststudykorean.ai.NanoState
import com.dero.juststudykorean.ai.NanoStatus
import com.dero.juststudykorean.data.backup.BackupExporter
import com.dero.juststudykorean.data.backup.BackupImporter
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.documents.DocumentIndexer
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.Collections

/** docs/AUDIT_2.md H-10: Settings' import and export with their errors, on the real importer and exporter over the golden base. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SettingsViewModelTest {
    private val app = GoldenApp()
    private val files = DocumentFiles(app.context)
    private val model = object : NanoModel {
        override suspend fun status() = NanoStatus.Unavailable
        override fun download(): Flow<NanoStatus> = emptyFlow()
        override suspend fun generate(system: String, prompt: String, maxTokens: Int, temperature: Float?) = ""
    }
    private val vm = SettingsViewModel(
        app.prefs, BackupImporter(app.context, app.db), BackupExporter(app.context, app.db),
        DocumentIndexer(app.db, app.clock, app.progress, app.notes, files), NanoState(model), app.words,
    )
    private val messages = Collections.synchronizedList(mutableListOf<String>())
    private val collector = CoroutineScope(Dispatchers.IO).launch { vm.messages.collect { messages += it } }

    @After
    fun close() { collector.cancel(); app.close() }

    private fun done() { idleUntil { !vm.busy.value && (vm.error.value != null || messages.isNotEmpty()) } }

    @Test
    fun importingABackupSaysHowMuchAndNeverStaysBusy() {
        vm.import(Uri.fromFile(File("../golden/db/fixture.jsbackup.zip")))
        done()
        assertTrue(messages.single().startsWith("Imported "))
        assertNull(vm.error.value)
        assertFalse(vm.importing.value)
        // H-03 B: word_state is not in the backup; the import writes it again from the imported data (golden: 4 learned).
        kotlinx.coroutines.runBlocking {
            assertTrue(app.prefs.wordStateBuiltV6.first())
            assertEquals(4, app.words.learned())
        }
    }

    @Test
    fun aFileThatIsNotABackupChangesNothingAndSaysSo() {
        vm.import(Uri.fromFile(File("../golden/db/does-not-exist.zip")))
        done()
        assertEquals("That file is not a Just Study backup. Nothing was changed.", vm.error.value)
        assertFalse(vm.importing.value)
    }

    @Test
    fun anExportThatFailsSaysItCouldNotSave() {
        val uri = Uri.parse("content://juststudy.test/broken.zip")
        shadowOf(app.context.contentResolver).registerOutputStream(uri, object : OutputStream() {
            override fun write(b: Int) { throw IOException("Broken pipe") }
        })
        vm.export(uri)
        done()
        assertEquals("Couldn't save the backup.", vm.error.value)
    }

    @Test
    fun anExportOnAFullPhoneSaysSo() {
        val uri = Uri.parse("content://juststudy.test/full.zip")
        shadowOf(app.context.contentResolver).registerOutputStream(uri, object : OutputStream() {
            override fun write(b: Int) { throw IOException("write failed: ENOSPC (No space left on device)") }
        })
        vm.export(uri)
        done()
        assertEquals("Not enough space on the phone. Free up some space and try again.", vm.error.value)
    }
}
