package com.dero.juststudykorean.ui.beta

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ui.components.MoreSheetContent
import com.dero.juststudykorean.ui.navigation.Screen
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** design/beta.md 1: More opens BETA, and each card's Open opens its screen (Coming soon until its own PR). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class BetaTest {
    @get:Rule(order = 0)
    val activity = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            val name = ComponentName(context.packageName, ComponentActivity::class.java.name)
            shadowOf(context.packageManager).apply {
                addActivityIfNotPresent(name)
                addIntentFilterForActivity(name, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Test
    fun eachOpenOpensItsScreenInTheDesignsOrder() {
        val opened = mutableListOf<Screen>()
        compose.setContent { JustStudyTheme { BetaScreen(onBack = {}, onOpen = { opened += it }) } }
        for (tag in listOf("listening", "grammar", "mistakes", "reader")) compose.onNodeWithTag("beta-open-$tag").performClick()
        assertEquals(listOf(Screen.Listening, Screen.GrammarLab(null), Screen.Mistakes, Screen.Reader(null)), opened)
        compose.onNodeWithText("Time here doesn't count toward your streak.").assertExists() // D11-2
    }

    @Test
    fun moreHasABetaRowThatOpensBeta() {
        var beta = 0
        compose.setContent { JustStudyTheme { MoreSheetContent(darkMode = false, onDarkMode = {}, onOpen = {}, onBeta = { beta++ }) } }
        compose.onNodeWithText("Beta").performClick()
        assertEquals(1, beta)
    }
}
