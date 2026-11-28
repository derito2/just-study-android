package com.dero.juststudykorean

import android.app.Application
import com.dero.juststudykorean.data.prefs.UserPrefs
import com.dero.juststudykorean.reminders.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class JustStudyApp : Application() {
    @Inject lateinit var prefs: UserPrefs
    @Inject lateinit var reminders: ReminderScheduler

    override fun onCreate() {
        super.onCreate()
        // D11-7: without RECEIVE_BOOT_COMPLETED a reboot drops the alarm; opening the app once sets it again.
        CoroutineScope(Dispatchers.IO).launch { reminders.schedule(prefs.reminder.first()) }
    }
}
