package com.dero.juststudykorean.ui.teacher

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.coach.CoachEngine
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.documents.DocumentIndexer
import com.dero.juststudykorean.data.teacher.TeacherService
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Teacher's risks 3 and 5 (docs/FASE_5_PLAN.md) and the route of a clear question, on the golden fixture. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TeacherBehaviourTest {
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

    private val app = GoldenApp().also { a ->
        // As the app after an import: the notes index (notes_fts) is rebuilt from extracted/.
        runBlocking { DocumentIndexer(a.db, a.clock, a.progress, a.notes, DocumentFiles(a.context)).rebuildIndex() }
    }
    private val vm = TeacherViewModel(
        TeacherService(app.db), CoachEngine(app.db, app.clock, app.progress, app.flashcards, app.notes), app.quiz, app.flashcards,
    )

    @After
    fun close() = app.close()

    private fun answered() = vm.state.value.exchanges.isNotEmpty() && !vm.state.value.busy

    @Test
    fun sendDisabledUnderTwoChars() {
        assertTrue(!canSend("") && !canSend(" a ") && canSend("ab"))
        var text by mutableStateOf("a")
        compose.setContent { JustStudyTheme(darkTheme = false) { TeacherContent(TeacherUi(), TextFieldValue(text), {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}) } }
        compose.onNodeWithText("SEND").assertIsNotEnabled()
        text = "ab"
        compose.onNodeWithText("SEND").assertIsEnabled()
        vm.send("a")
        assertTrue(vm.state.value.exchanges.isEmpty())
    }

    @Test
    fun maxLengthCut() {
        assertEquals(500, cut("가".repeat(2000)).length)
        vm.send("What does 근처 mean? " + "x".repeat(2000))
        idleUntil { answered() }
        assertEquals(500, vm.state.value.exchanges.single().question.length)
        assertTrue(vm.state.value.exchanges.single().notes != null) // a long question is answered, nothing hangs
    }

    @Test
    fun conversationSurvivesTabSwitch() {
        vm.send("What does 근처 mean?")
        idleUntil { answered() }
        var tab by mutableIntStateOf(0)
        compose.setContent {
            val ui by vm.state.collectAsState()
            JustStudyTheme(darkTheme = false) {
                if (tab == 0) TeacherContent(ui, TextFieldValue(), {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}) else Text("Dashboard")
            }
        }
        compose.onAllNodesWithText("What does 근처 mean?").assertCountEquals(2) // your bubble and the card's header
        tab = 1
        compose.waitForIdle()
        compose.onNodeWithText("Dashboard").assertExists()
        tab = 0
        compose.waitForIdle()
        compose.onAllNodesWithText("What does 근처 mean?").assertCountEquals(2)
        val answer = vm.state.value.exchanges.single().notes!!.answer
        assertTrue(answer, "근처 = Nearby" in answer)
    }

    @Test
    fun clearQuestionsGoToTheirPlace() {
        assertEquals(TeacherMode.Notes, route("What does 근처 mean?", TeacherMode.Coach))
        assertEquals(TeacherMode.Coach, route("Quiz on my mistakes", TeacherMode.Notes))
        assertEquals(TeacherMode.Coach, route("I have 20 minutes", TeacherMode.Notes))
        assertEquals(TeacherMode.Notes, route("hello", TeacherMode.Notes))
        assertEquals(TeacherMode.Coach, route("hello", TeacherMode.Coach))
    }

    @Test
    fun coachDeckIsCreatedAndOpens() {
        vm.setMode(TeacherMode.Coach)
        vm.send("Deck from my mistakes")
        idleUntil { answered() }
        val e = vm.state.value.exchanges.single()
        assertTrue(e.coach!!.deck != null)
        vm.createDeck(e.id)
        idleUntil { vm.state.value.exchanges.single().deck is DeckState.Created }
        compose.setContent {
            val ui by vm.state.collectAsState()
            JustStudyTheme(darkTheme = false) { TeacherContent(ui, TextFieldValue(), {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}) }
        }
        compose.onNodeWithText("OPEN FLASHCARDS").performScrollTo().assertExists().performClick()
    }
}
