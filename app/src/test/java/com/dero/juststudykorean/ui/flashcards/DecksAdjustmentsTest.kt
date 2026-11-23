package com.dero.juststudykorean.ui.flashcards

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.flashcards.Deck
import com.dero.juststudykorean.data.flashcards.FlashcardStats
import com.dero.juststudykorean.data.flashcards.LastCard
import com.dero.juststudykorean.data.prefs.DecksLayout
import com.dero.juststudykorean.korean.Scheduling
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** design/ajustes.md AJ-25..AJ-29: the Flashcards decks header, edit mode, list view, fixed height and marquee. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class DecksAdjustmentsTest {
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

    private fun deck(id: Long, name: String, due: Int = 3) =
        Deck(id, false, false, emptyList(), name, 20, 5, 1, due, 5 + due, null, LastCard("도서관", ""), null)

    private val decks = listOf(deck(1, "Unit 3"), deck(2, "Lesson 5 · Verbs"))

    private fun show(layout: DecksLayout = DecksLayout.Grid, onLayout: (DecksLayout) -> Unit = {}) = compose.setContent {
        JustStudyTheme(darkTheme = false) {
            DecksContent(
                DecksUi(decks, FlashcardStats(40, 6, 2, 0, 10, null)), Scheduling(), SnackbarHostState(), {}, {}, {}, {}, { _, _ -> }, {},
                { _, _ -> true }, {}, { null }, layout = layout, onLayout = onLayout,
            )
        }
    }

    // AJ-25
    @Test
    fun headerSaysDecksCreated() {
        show()
        compose.onNodeWithText("Decks Created").assertExists()
    }

    @Test
    fun penTogglesEditModeWithoutDone() {
        show()
        compose.onNodeWithContentDescription("Edit decks").performClick()
        compose.onNodeWithContentDescription("Done editing decks")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "On"))
        compose.onNodeWithText("Done", ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Done editing decks").performClick()
        compose.onNodeWithContentDescription("Edit decks").assertExists()
    }

    @Test
    fun slidersSwitchesTheLayout() {
        var layout by mutableStateOf(DecksLayout.Grid)
        val asked = mutableListOf<DecksLayout>()
        compose.setContent {
            JustStudyTheme(darkTheme = false) {
                DecksContent(
                    DecksUi(decks, FlashcardStats(40, 6, 2, 0, 10, null)), Scheduling(), SnackbarHostState(), {}, {}, {}, {}, { _, _ -> }, {},
                    { _, _ -> true }, {}, { null }, layout = layout, onLayout = { asked += it; layout = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Show decks as a list").performClick()
        compose.onNodeWithContentDescription("Show decks as a grid").performClick()
        assertEquals(listOf(DecksLayout.List, DecksLayout.Grid), asked)
    }

    @Test
    fun slidersStaysOnWhileEditing() {
        show()
        compose.onNodeWithContentDescription("Edit decks").performClick()
        compose.onNodeWithContentDescription("Show decks as a list").assertExists()
    }
}
