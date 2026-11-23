package com.dero.juststudykorean.ui.flashcards

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.dero.juststudykorean.ui.components.DeckCard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
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

    private fun deck(id: Long, name: String, due: Int = 3, ready: Boolean = true) =
        Deck(id, false, false, emptyList(), name, 20, 5, 1, due, if (ready) 5 + due else 0, null, LastCard("도서관", ""), null)

    private val decks = listOf(deck(1, "Unit 3"), deck(2, "Lesson 5 · Verbs"), deck(3, "Nothing today", 0, ready = false))
    private val studied = mutableListOf<String>()

    private fun show(layout: DecksLayout = DecksLayout.Grid, onLayout: (DecksLayout) -> Unit = {}) = compose.setContent {
        JustStudyTheme(darkTheme = false) {
            DecksContent(
                DecksUi(decks, FlashcardStats(40, 6, 2, 0, 10, null)), Scheduling(), SnackbarHostState(), {}, { studied += it.name }, {}, {}, { _, _ -> }, {},
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

    // AJ-26
    @Test
    fun editModeHasTheCornerCrossAndNoLineOrContinue() {
        show()
        compose.onAllNodesWithText("Continue", substring = true, ignoreCase = true).assertCountEquals(2)
        compose.onNodeWithContentDescription("Edit decks").performClick()
        compose.onNodeWithText("Drag to move", substring = true).assertDoesNotExist()
        compose.onAllNodesWithText("Continue", substring = true, ignoreCase = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Delete Unit 3").performClick()
        compose.onNodeWithText("Yes, delete", ignoreCase = true, substring = true).assertExists()
    }

    // AJ-27
    @Test
    fun listRowOpensTheDeckAsAButton() {
        show(DecksLayout.List)
        compose.onNodeWithText("Due now").assertDoesNotExist() // no summary in the list view
        compose.onNodeWithContentDescription("Open Unit 3")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(listOf("Unit 3"), studied)
        compose.onAllNodesWithText("Continue", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun listRowDoesNotOpenWhileEditing() {
        show(DecksLayout.List)
        compose.onNodeWithContentDescription("Edit decks").performClick()
        compose.onNodeWithContentDescription("Open Unit 3").assertDoesNotExist()
        compose.onNodeWithContentDescription("Delete Unit 3").assertExists()
        assertEquals(emptyList<String>(), studied)
    }

    @Test
    fun listRowWithNothingDueShowsTheSnackbar() {
        show(DecksLayout.List)
        compose.onNodeWithContentDescription("Open Nothing today").performClick()
        compose.onNodeWithText("Nothing due in this deck.").assertExists()
        assertEquals(emptyList<String>(), studied)
    }

    // AJ-28
    private fun heights(asRow: Boolean, fontScale: Float = 1f): List<Float> {
        val variants = listOf(
            deck(1, "Unit 3") to false,
            deck(2, "A very long deck name that never fits on one line, not even on a tablet") to false,
            Deck(3, true, false, emptyList(), "Nothing due", 20, 0, 0, 0, 0, null, null, null) to false,
            deck(4, "Unit 3") to true,
        )
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale), LocalFrozenMotion provides true) {
                JustStudyTheme(darkTheme = false) {
                    Column(Modifier.width(if (asRow) 379.dp else 183.5.dp).verticalScroll(rememberScrollState())) {
                        variants.forEachIndexed { i, (deck, editing) ->
                            DeckCard(deck, {}, {}, Modifier.testTag("deck$i"), editing = editing, asRow = asRow)
                        }
                    }
                }
            }
        }
        return variants.indices.map { i -> compose.onNodeWithTag("deck$i").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value } }
    }

    @Test
    fun gridDecksHaveOneFixedHeight() = heights(asRow = false).let { h -> h.forEach { assertEquals(h.toString(), 230f, it, 0.5f) } }

    @Test
    fun listRowsHaveOneFixedHeight() = heights(asRow = true).forEach { assertEquals(72f, it, 0.5f) }

    @Test
    fun fixedHeightGrowsWithLargeText() {
        heights(asRow = true, fontScale = 1.3f).forEach { assertEquals(72f * 1.3f, it, 0.5f) }
    }

    // AJ-29
    private val long = "A very long deck name that never fits on one line"

    private fun showName(asRow: Boolean) = compose.setContent {
        JustStudyTheme(darkTheme = false) {
            Column(Modifier.width(if (asRow) 379.dp else 183.5.dp)) { DeckCard(deck(1, long), {}, {}, asRow = asRow) }
        }
    }

    @Test
    fun longNameScrollsWholeOnOneLineInAList() {
        animations(1f)
        showName(asRow = true)
        compose.onNodeWithText(long).assertExists()
        compose.onNodeWithContentDescription(long).assertExists()
    }

    private fun animations(scale: Float) = android.provider.Settings.Global.putFloat(
        ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, scale,
    )

    @Test
    fun longNameScrollsWholeOnOneLine() {
        animations(1f)
        showName(asRow = false)
        compose.onNodeWithText(long).assertExists() // the whole name, moved by the marquee
        compose.onNodeWithContentDescription(long).assertExists()
    }

    @Test
    fun withReduceMotionTheNameIsShortenedInTheMiddleAndStill() {
        animations(0f)
        try {
            showName(asRow = false)
            compose.onNodeWithText(long).assertDoesNotExist()
            compose.onNodeWithText("…", substring = true).assertExists()
            compose.onNodeWithContentDescription(long).assertExists() // TalkBack still reads it whole
        } finally {
            animations(1f)
        }
    }
}
