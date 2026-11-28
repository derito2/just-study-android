package com.dero.juststudykorean.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dero.juststudykorean.MainActivity
import com.dero.juststudykorean.R
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.prefs.UserPrefs
import com.dero.juststudykorean.data.progress.ProgressRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** design/beta.md 8: numbers only, never text of your notes (the lock screen). null: nothing to say. */
internal fun reminderText(due: Int, streak: Int, studiedToday: Boolean): String? {
    val streakText = "$streak-day streak"
    return when {
        due > 0 -> if (streak > 0) "$due ${if (due == 1) "card" else "cards"} due · $streakText" else "$due ${if (due == 1) "card" else "cards"} due"
        !studiedToday && streak > 0 -> "$streakText — keep it going"
        else -> null // nothing due and studied today (or nothing to count)
    }
}

/** Posts the reminder, if there is something to say and notifications are on. Touching it opens All decks' session. */
internal fun remind(context: Context, due: Int, streak: Int, studiedToday: Boolean) {
    val text = reminderText(due, streak, studiedToday) ?: return
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
            enableVibration(false) // the phone never vibrates (R-010, D11-7)
            setSound(null, null)
        },
    )
    val intent = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_STUDY_ALL)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val open = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    manager.notify(
        NOTIFICATION_ID,
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_processing)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build(),
    )
}

internal const val CHANNEL = "reminders"
private const val NOTIFICATION_ID = 5

/** The daily alarm (not exported: only ReminderScheduler's explicit PendingIntent reaches it). Logs nothing. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var cards: FlashcardRepository
    @Inject lateinit var progress: ProgressRepository
    @Inject lateinit var prefs: UserPrefs
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val summary = progress.summary()
                val t = summary.today
                remind(context, cards.statsNow().dueNow, summary.streakDays, t.questions + t.sessions + t.cards > 0 || t.studySeconds > 0)
            } finally {
                scheduler.schedule(prefs.reminder.first()) // tomorrow's
                done.finish()
            }
        }
    }
}
