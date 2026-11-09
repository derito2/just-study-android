package com.dero.juststudykorean.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dero.juststudykorean.ui.components.JsBottomBar
import com.dero.juststudykorean.ui.components.MoreSheet
import com.dero.juststudykorean.ui.screens.PlaceholderScreen
import com.dero.juststudykorean.ui.dashboard.DashboardScreen
import com.dero.juststudykorean.ui.screens.SettingsScreen

/**
 * PhoneLayout (design/shell.md): each screen draws its own JsTopBar under the status bar; JsBottomBar goes to the
 * bottom edge, under the system navigation buttons. Edge to edge.
 */
@Composable
fun AppScaffold(darkMode: Boolean, onDarkMode: (Boolean) -> Unit, nav: AppNavState = rememberAppNavState()) {
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    // One decorator per tab stack, so a tab keeps its screens' state while another tab is shown.
    val entries = TopLevel.entries.associateWith { tab ->
        rememberDecoratedNavEntries(
            backStack = nav.stacks.getValue(tab),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = { route ->
                NavEntry(route, contentKey = "${route.tab}/${route.section}") {
                    if (route.section == MoreDestination.Settings) {
                        SettingsScreen(onMore = { moreOpen = true }, onBack = { nav.back() })
                    } else if (route == Route(TopLevel.Dashboard)) {
                        // shortcut: the run id is not passed on yet; the Quiz screen (task 5) will open it.
                        DashboardScreen(onMore = { moreOpen = true }, onOpenQuiz = { nav.select(TopLevel.Quiz) }, onAsk = { nav.select(TopLevel.Teacher) })
                    } else {
                        PlaceholderScreen(
                            route,
                            onMore = { moreOpen = true },
                            onBack = if (route.section != null) ({ nav.back() }) else null,
                        )
                    }
                }
            },
        )
    }
    Scaffold(
        bottomBar = { JsBottomBar(current = nav.current, onSelect = nav::select) },
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
