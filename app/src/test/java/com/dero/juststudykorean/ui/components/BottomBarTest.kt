package com.dero.juststudykorean.ui.components

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ui.navigation.MoreDestination
import com.dero.juststudykorean.ui.navigation.TopLevel
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** design/ajustes.md AJ-01: seven destinations, 76 dp each, scrolling sideways; Games opens on top and is never active. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class BottomBarTest {
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

    private val names = listOf("Dashboard", "Library", "Flashcards", "Quiz", "Teacher", "Games", "Grammar")

    @Test
    fun sevenDestinationsInOrderEach76Dp() {
        compose.setContent { JustStudyTheme(darkTheme = false) { JsBottomBar(current = TopLevel.Dashboard, onSelect = {}) } }
        compose.onNodeWithText("Dash", useUnmergedTree = true).assertExists() // the visible label…
        val left = names.map { n -> compose.onNodeWithContentDescription(n).assertWidthIsEqualTo(76.dp).getUnclippedBoundsInRoot().left.value }
        assertEquals(left.sorted(), left) // …in this order
        compose.onNodeWithContentDescription("Dashboard").assertIsSelected()
    }

    @Test
    fun gamesOpensOnTopAndIsNeverActive() {
        var games = 0
        val picked = mutableListOf<TopLevel>()
        compose.setContent { JustStudyTheme(darkTheme = false) { JsBottomBar(current = TopLevel.Library, onSelect = { picked += it }, onGames = { games++ }) } }
        compose.onNodeWithContentDescription("Games").performClick().assertIsNotSelected()
        assertEquals(1, games)
        assertEquals(emptyList<TopLevel>(), picked)
    }

    @Test
    fun grammarIsADestination() {
        val picked = mutableListOf<TopLevel>()
        compose.setContent { JustStudyTheme(darkTheme = false) { JsBottomBar(current = TopLevel.Dashboard, onSelect = { picked += it }) } }
        compose.onNodeWithContentDescription("Grammar").performScrollTo().performClick()
        assertEquals(listOf(TopLevel.Grammar), picked)
    }

    @Test
    fun anActiveDestinationOffScreenScrollsIntoView() {
        var current by mutableStateOf(TopLevel.Dashboard)
        compose.setContent { JustStudyTheme(darkTheme = false) { JsBottomBar(current = current, onSelect = { current = it }) } }
        val before = compose.onNodeWithContentDescription("Grammar").getUnclippedBoundsInRoot()
        assertTrue("Grammar starts out of view", before.right > 411.dp)
        current = TopLevel.Grammar
        compose.waitForIdle()
        val after = compose.onNodeWithContentDescription("Grammar").getUnclippedBoundsInRoot()
        assertTrue("Grammar is whole on screen: $after", after.left >= 0.dp && after.right <= 411.dp)
    }

    // AJ-02
    @Test
    fun moreHasCalendarQuizGuideDarkModeAndSettings() {
        val opened = mutableListOf<MoreDestination>()
        compose.setContent { JustStudyTheme(darkTheme = false) { MoreSheetContent(darkMode = false, onDarkMode = {}, onOpen = { opened += it }) } }
        val top = listOf("Calendar", "Quiz Guide", "Dark mode", "Settings").map { compose.onNodeWithText(it).getUnclippedBoundsInRoot().top.value }
        assertEquals(top.sorted(), top)
        compose.onNodeWithText("Games").assertDoesNotExist()
        compose.onNodeWithText("Grammar").assertDoesNotExist()
        compose.onNodeWithText("Quiz Guide").performClick()
        assertEquals(listOf(MoreDestination.QuizGuide), opened)
    }
}
