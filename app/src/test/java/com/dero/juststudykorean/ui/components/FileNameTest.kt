package com.dero.juststudykorean.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** design/polish.md P-04: the screen shows the short name, TalkBack reads the whole file name. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class FileNameTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun theScreenShowsTheShortNameAndTalkBackTheWholeOne() {
        val long = "Anki_vocabulary_with_a_very_long_name_2026.pdf"
        compose.setContent { JustStudyTheme(darkTheme = false) { FileName(long, maxLines = 1) } }
        compose.onNodeWithContentDescription(long).assertIsDisplayed()
        compose.onNodeWithText(displayName(long, 22)).assertIsDisplayed()
    }
}
