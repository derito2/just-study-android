package com.dero.juststudykorean.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * A screen on a tab's stack: the tab root, a More section opened on top of that tab, or a [Screen] on top
 * (a review session, a quiz…), which hides the bottom bar.
 */
data class Route(val tab: TopLevel, val section: MoreDestination? = null, val screen: Screen? = null)

/** Screens over a tab, without the bottom bar. */
sealed interface Screen {
    data class Study(val deckId: Long?, val deckName: String) : Screen
    data class Question(val runId: Long?) : Screen
    /** [key]: "run-{id}" or "session-{id}" (a history card). */
    data class Results(val key: String) : Screen
    /** Library's All vocabulary (the search sits above the keyboard, so no bottom bar). */
    data object Vocabulary : Screen
    /** Games (design/games.md, D8-1): full screen over the tab it was opened from. */
    data object Games : Screen

    companion object {
        fun encode(s: Screen): String = when (s) {
            is Study -> "study|${s.deckId ?: ""}|${s.deckName}"
            is Question -> "question|${s.runId ?: ""}"
            is Results -> "results|${s.key}"
            Vocabulary -> "vocabulary|"
            Games -> "games|"
        }
        fun decode(text: String): Screen {
            val p = text.split("|", limit = 3)
            return when (p[0]) {
                "study" -> Study(p[1].toLongOrNull(), p.getOrElse(2) { "" })
                "question" -> Question(p[1].toLongOrNull())
                "vocabulary" -> Vocabulary
                "games" -> Games
                else -> Results(p.drop(1).joinToString("|"))
            }
        }
    }
}

/**
 * One stack per tab ("exit through home", as the Navigation 3 multiple back stacks recipe): Back at a tab root
 * goes to Dashboard; at Dashboard's root it leaves the app.
 */
class AppNavState(current: TopLevel, sections: Map<TopLevel, List<Route>>) {
    var current by mutableStateOf(current)
        private set

    val stacks: Map<TopLevel, SnapshotStateList<Route>> = TopLevel.entries.associateWith { tab ->
        mutableStateListOf(Route(tab)).apply { addAll(sections[tab].orEmpty()) }
    }

    /** The current tab shows a screen without the bottom bar. */
    val fullScreen: Boolean get() = stacks.getValue(current).last().screen != null

    /** Opens [screen] on top of [tab] (and makes it the current tab). [replace]: instead of the top screen. */
    fun push(tab: TopLevel, screen: Screen, replace: Boolean = false) {
        current = tab
        val stack = stacks.getValue(tab)
        if (replace && stack.last().screen != null) stack.removeAt(stack.lastIndex)
        stack.add(Route(tab, screen = screen))
    }

    /** Dashboard's stack, plus the current tab's stack on top. */
    val visibleTabs: List<TopLevel> get() = if (current == TopLevel.Dashboard) listOf(current) else listOf(TopLevel.Dashboard, current)

    /** Tapping the active tab takes it back to its root; another tab keeps its own stack. */
    fun select(tab: TopLevel) {
        if (tab == current) stacks.getValue(tab).let { it.removeRange(1, it.size) } else current = tab
    }

    /** Opens a More section on top of the current tab (back to it if it is already on the stack). */
    fun open(section: MoreDestination) {
        val stack = stacks.getValue(current)
        val at = stack.indexOf(Route(current, section))
        if (at >= 0) stack.removeRange(at + 1, stack.size) else stack.add(Route(current, section))
    }

    fun back() {
        val stack = stacks.getValue(current)
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else current = TopLevel.Dashboard
    }

    companion object {
        // Saved as names: "Quiz", then each tab as "Tab:Section,~screen" (a screen starts with "~"; "\u0001" separates).
        val saver = Saver<AppNavState, List<String>>(
            save = { s ->
                listOf(s.current.name) + s.stacks.map { (tab, stack) ->
                    tab.name + ":" + stack.drop(1).joinToString("\u0001") { r -> r.screen?.let { "~" + Screen.encode(it) } ?: r.section!!.name }
                }
            },
            restore = { saved ->
                val sections = saved.drop(1).associate { line ->
                    val (tab, list) = line.split(":", limit = 2)
                    TopLevel.valueOf(tab) to list.split("\u0001").filter { it.isNotEmpty() }.map { e ->
                        if (e.startsWith("~")) Route(TopLevel.valueOf(tab), screen = Screen.decode(e.drop(1))) else Route(TopLevel.valueOf(tab), MoreDestination.valueOf(e))
                    }
                }
                AppNavState(TopLevel.valueOf(saved.first()), sections)
            },
        )
    }
}

@Composable
fun rememberAppNavState(): AppNavState = rememberSaveable(saver = AppNavState.saver) { AppNavState(TopLevel.Dashboard, emptyMap()) }
