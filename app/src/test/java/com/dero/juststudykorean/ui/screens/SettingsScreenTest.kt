package com.dero.juststudykorean.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ai.NanoModel
import com.dero.juststudykorean.ai.NanoState
import com.dero.juststudykorean.ai.NanoStatus
import com.dero.juststudykorean.data.backup.BackupExporter
import com.dero.juststudykorean.data.backup.BackupImporter
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.documents.DocumentIndexer
import com.dero.juststudykorean.reminders.ReminderScheduler
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The whole Settings screen: it draws, the Daily Reminder switch turns on and off, and Import, Export and Licenses do
 * nothing while drawing, only when touched (PR 12 on the phone: they ran during composition and crashed).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class SettingsScreenTest {
    @get:Rule(order = 0)
    val activityInManifest = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            val name = ComponentName(context.packageName, ComponentActivity::class.java.name)
            shadowOf(context.packageManager).apply {
                addActivityIfNotPresent(name)
                addIntentFilterForActivity(name, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
            }
            shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = GoldenApp()
    private val model = object : NanoModel {
        override suspend fun status() = NanoStatus.Unavailable
        override fun download(): Flow<NanoStatus> = emptyFlow()
        override suspend fun generate(system: String, prompt: String, maxTokens: Int, temperature: Float?) = ""
    }
    private val vm = SettingsViewModel(
        app.prefs, BackupImporter(app.context, app.db), BackupExporter(app.context, app.db),
        DocumentIndexer(app.db, app.clock, app.progress, app.notes, DocumentFiles(app.context)), NanoState(model), app.words,
        ReminderScheduler(app.context, app.clock),
    )

    @After
    fun close() = app.close()

    private fun started(): Intent? = shadowOf(compose.activity).nextStartedActivityForResult?.intent

    @Test
    fun drawingRunsNothingAndEachRowActsOnlyWhenTouched() {
        compose.setContent { JustStudyTheme { SettingsScreen(onMore = {}, onBack = {}, vm = vm) } }
        compose.waitForIdle()
        assertNull("nothing is opened just by drawing", started())
        compose.onNodeWithText("Licenses", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Close licenses").assertDoesNotExist()

        compose.onNodeWithText("Import backup").performClick()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started()?.action)
        compose.onNodeWithText("Export backup").performClick()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, started()?.action)
        compose.onNodeWithText("Licenses").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Close licenses").assertExists()
    }

    @Test
    fun theDailyReminderSwitchTurnsOnAndOff() {
        compose.setContent { JustStudyTheme { SettingsScreen(onMore = {}, onBack = {}, vm = vm) } }
        val switch = compose.onNodeWithContentDescription("Daily Reminder")
        compose.onNodeWithText("8:00 PM").assertDoesNotExist()
        switch.performClick()
        idleUntil { runBlocking { app.prefs.reminder.first().on } }
        compose.onNodeWithText("8:00 PM").assertExists()
        switch.assertIsOn()
        assertEquals(1, alarms().size)
        switch.performClick()
        idleUntil { !runBlocking { app.prefs.reminder.first().on } }
        compose.onNodeWithText("8:00 PM").assertDoesNotExist()
        idleUntil { alarms().isEmpty() }
    }

    private fun alarms() = shadowOf(app.context.getSystemService(android.app.AlarmManager::class.java)).scheduledAlarms
}
