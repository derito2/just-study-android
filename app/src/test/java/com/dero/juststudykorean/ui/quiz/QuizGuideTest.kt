package com.dero.juststudykorean.ui.quiz

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** design/ajustes.md AJ-40/AJ-41: the Quiz Guide, its texts next to the form's, and the screen. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class QuizGuideTest {
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

    @Test
    fun everyTypeAndLevelHasItsTextInTheFormsOrder() {
        assertEquals(QUIZ_TYPES.keys.toList(), QUIZ_GUIDE.keys.toList())
        assertEquals(LEVELS.map { it.first }, LEVEL_GUIDE.keys.toList())
        assertEquals(GuidePill.AI, QUIZ_GUIDE.getValue("short_answer").pill)
        assertEquals(GuidePill.Practice, QUIZ_GUIDE.getValue(SPEAK).pill)
        assertEquals(setOf(GuidePill.Rules), (QUIZ_GUIDE - "short_answer" - SPEAK).values.map { it.pill }.toSet())
    }

    @Test
    fun theScreenShowsTypesThenDifficultyAndGoesBack() {
        var back = 0
        compose.setContent { JustStudyTheme(darkTheme = false) { QuizGuideScreen(onMore = {}, onBack = { back++ }) } }
        compose.onNodeWithText("What each question type asks and what Difficulty changes.").assertExists()
        compose.onNodeWithText(QUIZ_GUIDE.getValue("ko_to_en").text).assertExists()
        compose.onNodeWithTag("quiz-guide").performScrollToNode(hasText(LEVEL_GUIDE.getValue(3)))
        compose.onNodeWithText(DIFFICULTY_GUIDE).assertExists()
        compose.onNodeWithText("Practice · not scored").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, back)
    }
}
