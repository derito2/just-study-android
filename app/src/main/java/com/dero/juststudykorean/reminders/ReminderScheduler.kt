package com.dero.juststudykorean.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.dero.juststudykorean.data.prefs.Reminder
import com.dero.juststudykorean.data.time.AppClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** The next [minuteOfDay] after [now], in [zone] (today if it is still ahead, else tomorrow). */
internal fun nextAt(minuteOfDay: Int, now: Instant, zone: ZoneId): Instant {
    val today = now.atZone(zone).toLocalDate().atStartOfDay(zone).plusMinutes(minuteOfDay.toLong())
    return (if (today.toInstant().isAfter(now)) today else today.plusDays(1)).toInstant()
}

/**
 * F1 (PLAN_FUNCIONES_ESTUDIO): one inexact alarm a day, setWindow of 15 min. No SCHEDULE_EXACT_ALARM nor any other
 * permission; without RECEIVE_BOOT_COMPLETED (D11-7) it is set again when the app starts and when the setting changes.
 */
@Singleton
class ReminderScheduler @Inject constructor(@ApplicationContext private val context: Context, private val clock: AppClock) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    /** Explicit (only ReminderReceiver, not exported) and immutable. */
    private val alarm get() = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // Debuggable builds only (debug, local QA): a 1-minute window, so a phone test doesn't wait up to 15 min.
    private val windowMs get() =
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) 60_000L else WINDOW_MS

    fun schedule(r: Reminder) {
        if (!r.on) return cancel()
        alarms.setWindow(AlarmManager.RTC_WAKEUP, nextAt(r.minuteOfDay, clock.now(), clock.zone).toEpochMilli(), windowMs, alarm)
    }

    fun cancel() = alarms.cancel(alarm)

    companion object {
        const val WINDOW_MS = 15 * 60_000L
    }
}
