package com.dero.juststudykorean.ui.testing

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.robolectric.Shadows.shadowOf

/** Robolectric needs ComponentActivity declared before createComposeRule starts it: use it as @get:Rule(order = 0). */
class RegisterComponentActivity : ExternalResource() {
    override fun before() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val name = ComponentName(context.packageName, ComponentActivity::class.java.name)
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(name)
            addIntentFilterForActivity(name, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
    }
}
