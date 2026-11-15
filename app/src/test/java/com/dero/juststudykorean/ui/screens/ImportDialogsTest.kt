package com.dero.juststudykorean.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** design/polish.md P-50 and P-51: say what Import does, and never leave the screen dimmed with nothing on it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ImportDialogsTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun confirmationSaysEverythingIsReplacedAndCannotBeUndone() {
        var replaced = 0
        var cancelled = 0
        compose.setContent { JustStudyTheme { ImportDialogs(confirming = true, importing = false, onCancel = { cancelled++ }, onReplace = { replaced++ }) } }
        compose.onNodeWithText("Replace everything?").assertExists()
        compose.onNodeWithText("Everything in the app now is deleted and replaced with the backup. This can't be undone.").assertExists()
        compose.onNodeWithText("Cancel", ignoreCase = true).performClick()
        compose.onNodeWithText("Replace", ignoreCase = true).performClick()
        assertEquals(1 to 1, cancelled to replaced)
    }

    @Test
    fun importingShowsItsOwnMessageAndNothingToDismiss() {
        compose.setContent { JustStudyTheme { ImportDialogs(confirming = false, importing = true, onCancel = {}, onReplace = {}) } }
        compose.onNodeWithText("Importing your backup…").assertExists()
    }

    @Test
    fun nothingShowsWhenIdle() {
        compose.setContent { JustStudyTheme { ImportDialogs(confirming = false, importing = false, onCancel = {}, onReplace = {}) } }
        compose.onNodeWithText("Importing your backup…").assertDoesNotExist()
        compose.onNodeWithText("Replace everything?").assertDoesNotExist()
    }
}
