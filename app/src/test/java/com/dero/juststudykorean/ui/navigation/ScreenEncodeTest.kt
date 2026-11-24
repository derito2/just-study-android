package com.dero.juststudykorean.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/** ColorOS may kill the process: every screen on a stack comes back the same from its saved text (FASE_11_PLAN PR 3). */
class ScreenEncodeTest {
    private fun roundTrip(s: Screen) = assertEquals(s, Screen.decode(Screen.encode(s)))

    @Test
    fun everyScreenComesBackTheSame() {
        listOf(
            Screen.Study(3, "Unit 1 | verbs"), Screen.Study(null, ""), Screen.Question(7), Screen.Question(null),
            Screen.Results("run-4"), Screen.Vocabulary, Screen.Games,
            // Phase 11: BETA and its four screens.
            Screen.Beta, Screen.GrammarLab("-아서/어서"), Screen.GrammarLab("a|b"), Screen.GrammarLab(null),
            Screen.Listening, Screen.Mistakes, Screen.Reader(12), Screen.Reader(null),
        ).forEach(::roundTrip)
    }
}
