package com.dero.juststudykorean.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 2, task 3: one stack per tab, More sections on top of the current tab, Back. On the OPPO. */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun tab(name: String) = rule.onNode(isSelectable() and hasText(name))
    private fun title(name: String) = rule.onNode(isHeading() and hasText(name))
    private fun more(section: String) {
        rule.onNodeWithContentDescription("More").performClick()
        rule.onNodeWithText(section).performClick()
        rule.waitForIdle()
    }

    @Test
    fun startsOnDashboard() {
        title("Dashboard").assertIsDisplayed()
        tab("Dashboard").assertIsSelected()
    }

    @Test
    fun eachTabShowsItsTitle() {
        for (name in listOf("Library", "Flashcards", "Quiz", "Teacher", "Dashboard")) {
            tab(name).performClick()
            title(name).assertIsDisplayed()
            tab(name).assertIsSelected()
        }
    }

    @Test
    fun moreOpensSheetAndBackClosesIt() {
        rule.onNodeWithContentDescription("More").performClick()
        rule.onNodeWithText("Grammar").assertIsDisplayed()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithText("Grammar").assertDoesNotExist()
        title("Dashboard").assertIsDisplayed()
    }

    @Test
    fun grammarStacksOverCurrentTab() {
        tab("Quiz").performClick()
        more("Grammar")
        title("Grammar").assertIsDisplayed()
        Espresso.pressBack()
        title("Quiz").assertIsDisplayed()
    }

    @Test
    fun reselectTabPopsToRoot() {
        tab("Quiz").performClick()
        more("Grammar")
        tab("Quiz").performClick()
        title("Quiz").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun otherTabsKeepTheirStack() {
        tab("Flashcards").performClick()
        more("Calendar")
        tab("Dashboard").performClick()
        tab("Flashcards").performClick()
        title("Calendar").assertIsDisplayed()
    }
}
