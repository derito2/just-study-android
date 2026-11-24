package com.dero.juststudykorean.ui.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** docs/AUDIT_2.md H-10: Dashboard loads its figures, fails without hanging, retries, and Practice this makes a quiz or says why not. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class DashboardViewModelTest {
    private val app = GoldenApp()
    private val vm = DashboardViewModel(app.progress, app.quiz, app.prefs, app.words, app.clock)
    private val watcher = CoroutineScope(Dispatchers.IO).launch { vm.state.collect { } } // WhileSubscribed: someone has to listen

    @After
    fun close() { watcher.cancel(); app.close() }

    @Test
    fun theWeekIsNotAskedOfTheModel() { // design/ajustes.md AJ-10: no weekly summary, so Gemini Nano is not even reachable from here
        val params = DashboardViewModel::class.java.constructors.flatMap { it.parameterTypes.toList() }
        assertFalse(params.any { it == com.dero.juststudykorean.data.ai.GrammarAi::class.java })
        assertFalse(DashboardUi::class.java.declaredFields.any { it.name == "week" })
    }

    @Test
    fun loadsTheFiguresOfTheGoldenBase() {
        idleUntil { vm.state.value.summary != null }
        val ui = vm.state.value
        assertFalse(ui.error)
        assertNotNull(ui.weekly)
        assertNotNull(ui.calendar)
        assertNotNull(ui.recommended)
        assertNotNull(ui.timeline)
    }

    @Test
    fun whenTheDataCannotBeReadItSaysSoAndRetryDoesNotHang() {
        idleUntil { vm.state.value.summary != null }
        runBlocking { app.db.useWriterConnection { it.executeSQL("DROP TABLE flashcard_reviews") } } // a table the figures read is gone: their queries throw
        vm.retry()
        idleUntil { vm.state.value.error }
        assertNull(vm.state.value.summary)
        vm.retry()
        idleUntil { vm.state.value.error } // tried again, still failing, still answering
        assertTrue(vm.state.value.error)
    }

    @Test
    fun practiceOnATopicWithMaterialOpensAQuiz() {
        idleUntil { vm.state.value.recommended?.isNotEmpty() == true }
        val topic = vm.state.value.recommended!!.first().name
        var opened: Long? = null
        val c = CoroutineScope(Dispatchers.IO).launch { vm.openQuiz.collect { opened = it } }
        vm.practice(topic)
        idleUntil { opened != null || vm.state.value.notice != null }
        c.cancel()
        assertNotNull("a quiz id", opened)
    }

    @Test
    fun practiceOnATopicWithoutMaterialSaysWhy() {
        vm.practice("a topic nobody wrote about")
        idleUntil { vm.state.value.notice != null }
        assertEquals("Not enough material in your notes for that topic yet.", vm.state.value.notice)
    }
}
