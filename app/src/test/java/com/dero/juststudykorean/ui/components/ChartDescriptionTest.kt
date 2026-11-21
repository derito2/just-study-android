package com.dero.juststudykorean.ui.components

import com.dero.juststudykorean.data.progress.Timeline
import com.dero.juststudykorean.data.progress.TimelinePoint
import com.dero.juststudykorean.data.progress.TimelineUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** R1-27: the chart's description reads the period's values. */
class ChartDescriptionTest {
    private fun point(day: Int, minutes: Long, answers: Int, accuracy: Double?) =
        TimelinePoint(LocalDate.of(2026, 11, day), minutes, answers, accuracy, 0, 0, 0)

    @Test
    fun readsTheTotalsOfThePeriod() {
        val t = Timeline(TimelineUnit.Day, ZoneId.of("UTC"), listOf(point(1, 10, 4, 0.5), point(2, 5, 6, 1.0)))
        assertEquals("Progress over time, by day, last 2 days: 15 min studied, 10 quiz answers, 80% right.", chartDescription(t))
    }

    @Test
    fun theLeftAxisIsTheMinutesLineAndTheBarsHaveTheirOwnScale() {
        val points = listOf(point(1, 12, 4, 0.5), point(2, 0, 0, null)).map { it.copy(cardsNew = 500, cardsDue = 3) }
        assertEquals(20, minutesAxis(points)) // niceMax(12): the line, not the 500 cards
        assertEquals(5, minutesAxis(points.map { it.copy(minutes = 0) }))
        assertEquals(500, cardsScale(points))
        assertEquals(1, cardsScale(points.map { it.copy(cardsNew = 0, cardsDue = 0) }))
    }

    @Test
    fun theBarsAreSixDpOrThinnerWhenThereAreManyPeriods() {
        assertEquals(6f, barWidthDp(slotDp = 60f), 0.001f)
        assertEquals(2f, barWidthDp(slotDp = 5f), 0.001f) // never thinner than 2 dp
        assertEquals(4.2f, barWidthDp(slotDp = 18f), 0.001f)
    }
}
