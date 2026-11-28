package com.dero.juststudykorean.reminders

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** design/beta.md 8: the reminder's notification (numbers only, private, no vibration) and its receiver in the manifest. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ReminderReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun nothingDueAndStudiedTodayPostsNothing() {
        remind(context, due = 0, streak = 8, studiedToday = true)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun cardsDuePostAQuietPrivateNotificationWithNumbersOnly() {
        remind(context, due = 12, streak = 8, studiedToday = false)
        val n = shadowOf(manager).allNotifications.single()
        assertEquals("12 cards due · 8-day streak", n.extras.getString(NotificationCompat.EXTRA_TEXT))
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, n.visibility)
        val channel = manager.getNotificationChannel(CHANNEL)
        assertFalse("never vibrates", channel.shouldVibrate())
        assertNull(channel.sound)
        val open = shadowOf(n.contentIntent)
        assertTrue(open.isActivityIntent && open.isImmutable)
        assertEquals(MainActivity.ACTION_STUDY_ALL, open.savedIntent.action)
        assertEquals(ComponentName(context, MainActivity::class.java), open.savedIntent.component)
    }

    @Test
    fun theTextHasNoWordsOfTheNotes() {
        assertEquals("8-day streak — keep it going", reminderText(0, 8, studiedToday = false))
        assertEquals("1 card due", reminderText(1, 0, studiedToday = false))
        assertNull(reminderText(0, 0, studiedToday = false))
        assertNull(reminderText(0, 3, studiedToday = true))
    }

    /** Plan §8: the receiver is not exported and the app asks for no new permission (no boot, no exact alarm). */
    @Test
    fun manifestReceiverNotExportedAndNoNewPermission() {
        val pm = context.packageManager
        assertFalse(pm.getReceiverInfo(ComponentName(context, ReminderReceiver::class.java), 0).exported)
        val asked = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        val allowed = setOf(
            "android.permission.POST_NOTIFICATIONS", "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "android.permission.RECORD_AUDIO", "android.permission.USE_BIOMETRIC", "com.google.android.apps.aicore.service.BIND_SERVICE",
        )
        val extra = asked.filterNot { it in allowed || it.startsWith("com.dero.juststudykorean") }
        assertTrue("permissions not allowed: $extra", extra.isEmpty())
        assertFalse("android.permission.RECEIVE_BOOT_COMPLETED" in asked)
        assertFalse("android.permission.SCHEDULE_EXACT_ALARM" in asked)
    }
}
