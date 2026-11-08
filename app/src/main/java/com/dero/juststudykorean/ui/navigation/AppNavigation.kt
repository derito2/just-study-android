package com.dero.juststudykorean.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/** A screen on a tab's stack: the tab root ([section] null) or a More section opened on top of that tab. */
data class Route(val tab: TopLevel, val section: MoreDestination? = null)

/**
 * One stack per tab ("exit through home", as the Navigation 3 multiple back stacks recipe): Back at a tab root
 * goes to Dashboard; at Dashboard's root it leaves the app.
 */
class AppNavState(current: TopLevel, sections: Map<TopLevel, List<MoreDestination>>) {
    var current by mutableStateOf(current)
        private set

    val stacks: Map<TopLevel, SnapshotStateList<Route>> = TopLevel.entries.associateWith { tab ->
        mutableStateListOf(Route(tab)).apply { addAll(sections[tab].orEmpty().map { Route(tab, it) }) }
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
        // Saved as names: "Quiz", then each tab as "Tab:Section,Section".
        val saver = Saver<AppNavState, List<String>>(
            save = { s -> listOf(s.current.name) + s.stacks.map { (tab, stack) -> tab.name + ":" + stack.drop(1).joinToString(",") { it.section!!.name } } },
            restore = { saved ->
                val sections = saved.drop(1).associate { line ->
                    val (tab, list) = line.split(":")
                    TopLevel.valueOf(tab) to list.split(",").filter { it.isNotEmpty() }.map(MoreDestination::valueOf)
                }
                AppNavState(TopLevel.valueOf(saved.first()), sections)
            },
        )
    }
}

@Composable
fun rememberAppNavState(): AppNavState = rememberSaveable(saver = AppNavState.saver) { AppNavState(TopLevel.Dashboard, emptyMap()) }
