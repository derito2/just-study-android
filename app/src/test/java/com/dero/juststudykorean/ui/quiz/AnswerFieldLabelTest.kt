package com.dero.juststudykorean.ui.quiz

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.data.quiz.QuizRun
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Phase 7, task 5: TalkBack reads the answer field's name (it was an unnamed edit box on the OPPO). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AnswerFieldLabelTest {
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
    fun theAnswerFieldHasAName() {
        val q = QuestionsEntity(1, "write_ko", "library", null, "도서관", null, 1, null, "[\"1:4:0\"]", null, "rules", 0)
        compose.setContent { JustStudyTheme(darkTheme = false) { QuestionContent(QuestionUi(loading = false, run = QuizRun(1, listOf(q))), {}, {}, {}, {}) } }
        compose.onNodeWithContentDescription("Your answer in Korean").assertExists()
    }

    @Test
    fun aFieldIsNamedByItsPlaceholder() {
        compose.setContent { JustStudyTheme(darkTheme = false) { com.dero.juststudykorean.ui.components.JsTextField("", {}, placeholder = "Search in Korean or English") } }
        compose.onNodeWithContentDescription("Search in Korean or English").assertExists()
    }
}
