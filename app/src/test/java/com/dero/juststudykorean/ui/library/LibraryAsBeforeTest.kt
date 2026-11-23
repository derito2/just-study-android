package com.dero.juststudykorean.ui.library

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.documents.LibraryDocument
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId

/** AJ-20: Library as in 0.11.0 (empty line, Delete button, Reprocess icon), keeping the Notes template link. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class LibraryAsBeforeTest {
    @get:Rule(order = 0)
    val activity = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            val name = ComponentName(context.packageName, ComponentActivity::class.java.name)
            shadowOf(context.packageManager).apply {
                addActivityIfNotPresent(name)
                addIntentFilterForActivity(name, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun show(docs: List<LibraryDocument>) = compose.setContent {
        JustStudyTheme(darkTheme = false) {
            LibraryContent(
                DocumentsState(docs), false, null, emptyMap(), null, SnackbarHostState(), ZoneId.of("UTC"),
                {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {},
            )
        }
    }

    @Test
    fun emptyIsOneLineWithTheTemplateLink() {
        show(emptyList())
        compose.onNodeWithText("No documents yet. Upload a PDF and get to work.").assertExists()
        compose.onNodeWithText("How to write notes: Notes template").assertExists()
        compose.onNodeWithText("All vocabulary", substring = true, ignoreCase = true).assertExists()
    }

    @Test
    fun cardHasDeleteButtonAndReprocessIcon() {
        val doc = DocumentsEntity(1, "Notes.pdf", "1.pdf", "sha1", 2, "ready", true, 0, 0, 100, null, null, 0, null)
        show(listOf(LibraryDocument(doc, emptyList(), null)))
        compose.onNodeWithText("Delete", substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithContentDescription("Reprocess").assertExists()
    }
}
