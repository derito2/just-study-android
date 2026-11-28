package com.dero.juststudykorean.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.prefs.Reminder
import com.dero.juststudykorean.data.time.FixedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/** F1 (PLAN_FUNCIONES_ESTUDIO): switched on, one inexact alarm at the next reminder time; switched off, none. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ReminderSchedulerTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private fun at(h: Int, m: Int, day: Int = 8) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant()

    @Test
    fun onSetsOneAlarmAtTheNextTimeAndOffCancelsIt() {
        val scheduler = ReminderScheduler(context, FixedClock(at(18, 30), zone))
        scheduler.schedule(Reminder(on = true, minuteOfDay = 20 * 60))
        val alarm = alarms.scheduledAlarms.single()
        assertEquals(at(20, 0).toEpochMilli(), alarm.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        // Robolectric's app is debuggable: the 1-minute test window (15 min in release).
        assertEquals(60_000L, alarm.windowLengthMs)
        scheduler.schedule(Reminder(on = true, minuteOfDay = 20 * 60)) // again (app start): still one
        assertEquals(1, alarms.scheduledAlarms.size)
        scheduler.schedule(Reminder(on = false, minuteOfDay = 20 * 60))
        assertNull(alarms.nextScheduledAlarm)
    }

    @Test
    fun aTimeAlreadyPastTodayIsTomorrow() {
        assertEquals(at(20, 0), nextAt(20 * 60, at(18, 30), zone))
        assertEquals(at(8, 0, day = 9), nextAt(8 * 60, at(18, 30), zone))
        assertEquals(at(20, 0, day = 9), nextAt(20 * 60, at(20, 0), zone)) // just fired: tomorrow's
    }
}
