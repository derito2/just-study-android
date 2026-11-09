package com.dero.juststudykorean.ui.grammar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.grammar.GrammarFilter
import com.dero.juststudykorean.data.grammar.GrammarRepository
import com.dero.juststudykorean.data.progress.CalendarDay
import com.dero.juststudykorean.ui.calendar.CalendarViewModel
import com.dero.juststudykorean.ui.components.dayDescription
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/** Grammar and Calendar behaviour (plan task 6), with the real repositories on the golden base. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class GrammarCalendarTest {
    private val app = GoldenApp()

    @After
    fun close() = app.close()

    /** Keeps the WhileSubscribed state flows running, as the screen would. */
    private fun <T> watch(flow: kotlinx.coroutines.flow.Flow<T>) = CoroutineScope(Dispatchers.Main.immediate).launch { flow.collect { } }

    @Test
    fun searchFiltersWithinCategory() {
        val repo = GrammarRepository(app.db)
        val vm = GrammarViewModel(repo)
        val job = watch(vm.state)
        idleUntil { vm.state.value.points != null }
        val all = runBlocking { repo.all() }
        val category = all.groupBy { it.category }.maxBy { it.value.size }
        val filter = GrammarFilter.entries.first { it.category == category.key }
        val word = category.value.first().pattern.trim().take(2)
        vm.setFilter(filter)
        vm.setQuery(word)
        idleUntil { vm.state.value.query == word && vm.state.value.filter == filter && vm.state.value.points?.all { it.category == filter.category } == true }
        val shown = vm.state.value.points!!
        val q = word.lowercase()
        assertEquals(all.filter { it.category == filter.category && (q in it.pattern.lowercase() || q in it.meaning.lowercase()) }.map { it.id }, shown.map { it.id })
        assertTrue(shown.isNotEmpty())
        job.cancel()
    }

    @Test
    fun onlyOneCardOpen() {
        val vm = GrammarViewModel(GrammarRepository(app.db))
        val job = watch(vm.state)
        idleUntil { !vm.state.value.points.isNullOrEmpty() }
        val (a, b) = vm.state.value.points!!.take(2)
        vm.toggle(a.id); idleUntil { vm.state.value.open == a.id }
        vm.toggle(b.id); idleUntil { vm.state.value.open == b.id } // opening b closes a
        vm.toggle(b.id); idleUntil { vm.state.value.open == null }
        assertNull(vm.state.value.open)
        job.cancel()
    }

    @Test
    fun nextMonthDisabledOnCurrentMonth() {
        val vm = CalendarViewModel(app.progress, app.clock)
        val job = watch(vm.state)
        idleUntil { vm.state.value.data != null }
        assertEquals(YearMonth.of(2026, 11), vm.state.value.month)
        assertFalse(vm.state.value.canGoNext)
        vm.next()
        idleUntil { true }
        assertEquals(YearMonth.of(2026, 11), vm.state.value.month)
        vm.previous()
        idleUntil { vm.state.value.month == YearMonth.of(2026, 10) && vm.state.value.data?.month == 10 }
        assertTrue(vm.state.value.canGoNext)
        job.cancel()
    }

    @Test
    fun dayDetailText() {
        // The web's describe() (CalendarMonth.tsx), written out by hand for the same days.
        fun day(d: Int, studied: Boolean, q: Int, ok: Int, min: Long, topics: List<String>, status: String) =
            CalendarDay(LocalDate.of(2026, 11, d), studied, min, q, ok, if (q == 0) null else ok.toDouble() / q, topics, status)
        assertEquals("11월 8일: 15 questions, 80 % accuracy, 25 min, topics: particles, -아서/어서",
            dayDescription(day(8, true, 15, 12, 25, listOf("particles", "3. -아서/어서"), "green"), 11))
        assertEquals("11월 9일: not studied", dayDescription(day(9, false, 0, 0, 0, emptyList(), "none"), 11))
        assertEquals("11월 3일: 1 question, 0 % accuracy, 2 min", dayDescription(day(3, true, 1, 0, 2, emptyList(), "red"), 11))
        // …and the real month of the golden base reads the same as its golden calendar
        val month = runBlocking { app.progress.calendarNow(2026, 11) }
        month.days.filter { it.studied }.forEach { d ->
            val topics = if (d.topics.isNotEmpty()) ", topics: ${d.topics.joinToString(", ") { it.replace(Regex("^\\s*\\d{1,3}[.)]\\s+"), "") }}" else ""
            assertEquals("11월 ${d.date.dayOfMonth}일: ${d.questions} question${if (d.questions == 1) "" else "s"}, ${Math.round(d.accuracy!! * 100)} % accuracy, ${d.minutes} min$topics", dayDescription(d, 11))
        }
    }
}
