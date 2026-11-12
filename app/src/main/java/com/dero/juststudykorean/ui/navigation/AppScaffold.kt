package com.dero.juststudykorean.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dero.juststudykorean.ui.components.JsBottomBar
import com.dero.juststudykorean.ui.components.MoreSheet
import com.dero.juststudykorean.ui.screens.PlaceholderScreen
import com.dero.juststudykorean.ui.calendar.CalendarScreen
import com.dero.juststudykorean.ui.dashboard.DashboardScreen
import com.dero.juststudykorean.ui.grammar.GrammarScreen
import com.dero.juststudykorean.ui.library.AllVocabularyScreen
import com.dero.juststudykorean.ui.library.LibraryScreen
import com.dero.juststudykorean.ui.flashcards.DecksScreen
import com.dero.juststudykorean.ui.flashcards.StudyScreen
import com.dero.juststudykorean.ui.quiz.QuestionScreen
import com.dero.juststudykorean.ui.quiz.QuizHomeScreen
import com.dero.juststudykorean.ui.quiz.ResultsScreen
import com.dero.juststudykorean.ui.screens.SettingsScreen
import com.dero.juststudykorean.ui.teacher.TeacherScreen

/**
 * PhoneLayout (design/shell.md): each screen draws its own JsTopBar under the status bar; JsBottomBar goes to the
 * bottom edge, under the system navigation buttons. Edge to edge.
 */
@Composable
fun AppScaffold(darkMode: Boolean, onDarkMode: (Boolean) -> Unit, nav: AppNavState = rememberAppNavState()) {
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var coachAsks by rememberSaveable { mutableIntStateOf(0) } // Dashboard's "Ask what to do": Teacher in the coach's mode
    // One decorator per tab stack, so a tab keeps its screens' state while another tab is shown.
    val entries = TopLevel.entries.associateWith { tab ->
        rememberDecoratedNavEntries(
            backStack = nav.stacks.getValue(tab),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            entryProvider = { route ->
                NavEntry(route, contentKey = "${route.tab}/${route.section}/${route.screen}") {
                    val more = { moreOpen = true }
                    val back = { nav.back() }
                    val screen = route.screen
                    when {
                        screen is Screen.Study -> StudyScreen(screen.deckId, screen.deckName, onBack = back)
                        screen is Screen.Question -> QuestionScreen(
                            screen.runId, onLeave = back, onResults = { nav.push(TopLevel.Quiz, Screen.Results("run-$it"), replace = true) },
                            onCreated = { nav.push(TopLevel.Quiz, Screen.Question(it), replace = true) },
                        )
                        screen is Screen.Results -> ResultsScreen(screen.key, onClose = back, onNewQuiz = back)
                        screen is Screen.Vocabulary -> AllVocabularyScreen(onBack = back)
                        route == Route(TopLevel.Library) -> LibraryScreen(onMore = more, onAllVocabulary = { nav.push(TopLevel.Library, Screen.Vocabulary) })
                        route == Route(TopLevel.Quiz) -> QuizHomeScreen(
                            onMore = more, onGenerate = { nav.push(TopLevel.Quiz, Screen.Question(null)) },
                            onOpenRun = { nav.push(TopLevel.Quiz, Screen.Question(it)) }, onDetails = { nav.push(TopLevel.Quiz, Screen.Results(it)) },
                        )
                        route.section == MoreDestination.Settings -> SettingsScreen(onMore = more, onBack = back)
                        route.section == MoreDestination.Grammar -> GrammarScreen(onMore = more, onBack = back, onLibrary = { nav.select(TopLevel.Library) })
                        route.section == MoreDestination.Calendar -> CalendarScreen(onMore = more, onBack = back)
                        route == Route(TopLevel.Dashboard) -> DashboardScreen(
                            onMore = more, onOpenQuiz = { nav.push(TopLevel.Quiz, Screen.Question(it)) }, onAsk = { coachAsks++; nav.select(TopLevel.Teacher) },
                        )
                        route == Route(TopLevel.Teacher) -> TeacherScreen(
                            coachAsks, onMore = more, onOpenQuiz = { nav.push(TopLevel.Quiz, Screen.Question(it)) },
                            onOpenFlashcards = { nav.select(TopLevel.Flashcards) },
                        )
                        route == Route(TopLevel.Flashcards) -> DecksScreen(onMore = more, onStudy = { nav.push(TopLevel.Flashcards, Screen.Study(it.id, it.name)) })
                        else -> PlaceholderScreen(route, onMore = more, onBack = if (route.section != null || screen != null) back else null)
                    }
                }
            },
        )
    }
    Scaffold(
        bottomBar = { if (!nav.fullScreen) JsBottomBar(current = nav.current, onSelect = nav::select) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0), // the top bar handles the status bar; the bottom bar, the navigation bar
    ) { padding ->
        NavDisplay(
            entries = nav.visibleTabs.flatMap { entries.getValue(it) },
            modifier = Modifier.padding(padding),
            onBack = { nav.back() },
        )
    }
    if (moreOpen) {
        MoreSheet(
            darkMode = darkMode,
            onDarkMode = onDarkMode,
            onOpen = { moreOpen = false; nav.open(it) },
            onDismiss = { moreOpen = false },
        )
    }
}
