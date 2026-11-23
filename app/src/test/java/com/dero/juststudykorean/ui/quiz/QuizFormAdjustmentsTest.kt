package com.dero.juststudykorean.ui.quiz

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.prefs.QUIZ_MAX_COUNT
import com.dero.juststudykorean.data.prefs.QuizForm
import com.dero.juststudykorean.data.quiz.QuizRequest
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.components.Stepper
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.math.abs

/** design/ajustes.md AJ-30..AJ-32: Default-size buttons, Questions and Documents on one row, up to 25 questions. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class QuizFormAdjustmentsTest {
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

    private val longName = DocumentsEntity(4, "Korean notes week 15 with a very long name that never fits.pdf", "4.pdf", "sha4", 2, "ready", true, 0, 0, 100, null, null, 0, null)

    private fun form(selected: Set<Long> = emptySet()) = compose.setContent {
        JustStudyTheme(darkTheme = false) {
            Column {
                StickerButton("Reference", {}, Modifier.testTag("default"), ko = "퀴즈 만들기", color = StickerColor.Go) // AJ-30: Default size
                QuizHomeContent(QuizForm(documentIds = selected), listOf(longName), emptyList(), emptyList(), SnackbarHostState(), {}, {}, {}, {}, {}, {})
            }
        }
    }

    // AJ-30
    @Test
    fun generateAndPracticeAreDefaultSize() {
        form()
        val h = compose.onNodeWithTag("default").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        compose.onNodeWithText("Generate quiz", substring = true, ignoreCase = true).assertHeightIsEqualTo(h)
        compose.onNodeWithText("Practice saved questions", substring = true, ignoreCase = true).assertHeightIsEqualTo(h)
    }

    // AJ-31
    @Test
    fun questionsAndDocumentsShareOneRowEvenWithALongName() {
        form(selected = setOf(4))
        val stepper = compose.onNodeWithContentDescription("Questions: 5").getUnclippedBoundsInRoot()
        val field = compose.onNodeWithContentDescription(longName.originalName).getUnclippedBoundsInRoot()
        assertTrue("same row: $stepper / $field", abs(((stepper.top + stepper.bottom) / 2 - (field.top + field.bottom) / 2).value) < 16f)
        compose.onNodeWithText("1 to", substring = true).assertDoesNotExist()
    }

    @Test
    fun documentsFieldIsOneLine48Dp() {
        form(selected = setOf(4))
        compose.onNodeWithContentDescription(longName.originalName).assertHeightIsEqualTo(48.dp)
    }

    // AJ-31, AJ-32
    @Test
    fun stepperGoesTo25With40DpButtons() {
        compose.setContent { JustStudyTheme(darkTheme = false) { Stepper(25, {}, 1..QUIZ_MAX_COUNT, "Questions") } }
        compose.onNodeWithContentDescription("Questions: 25").assertExists()
        compose.onNodeWithContentDescription("More").assertIsNotEnabled().assertWidthIsEqualTo(40.dp)
        compose.onNodeWithContentDescription("Fewer").assertWidthIsEqualTo(40.dp)
    }

    // AJ-32
    @Test
    fun aSavedCountOver25ReadsAs25() {
        val app = GoldenApp()
        try {
            runBlocking { app.prefs.setQuizForm(QuizForm(count = 40)) }
            assertEquals(QUIZ_MAX_COUNT, runBlocking { app.prefs.quizForm.first().count })
        } finally {
            app.close()
        }
    }

    @Test
    fun theRulesMakeMoreThanTenQuestions() {
        val app = GoldenApp()
        try {
            val run = runBlocking { app.quiz.generate(QuizRequest(count = 25, types = QuizForm().types.toList(), seed = 7)) }!!
            assertTrue("${run.questions.size} questions", run.questions.size > 10)
        } finally {
            app.close()
        }
    }

    @Test
    fun difficultyTypesAreTheOnesTheModelWrites() { // R-017: Fill in the blank is made by the rules
        assertEquals(com.dero.juststudykorean.data.ai.AI_TYPES, LEVEL_TYPES)
    }
}
