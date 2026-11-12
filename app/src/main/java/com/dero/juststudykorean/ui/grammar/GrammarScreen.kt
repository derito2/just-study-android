package com.dero.juststudykorean.ui.grammar

import com.dero.juststudykorean.ui.components.GrammarCardAi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.data.grammar.GrammarFilter
import com.dero.juststudykorean.ui.components.EmptyState
import com.dero.juststudykorean.ui.components.ErrorState
import com.dero.juststudykorean.ui.components.FilterPill
import com.dero.juststudykorean.ui.components.GrammarCard
import com.dero.juststudykorean.ui.components.JsTextField
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.LoadingState
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

@Composable
fun GrammarScreen(onMore: () -> Unit, onBack: () -> Unit, onLibrary: () -> Unit, vm: GrammarViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    GrammarContent(ui, onMore, onBack, onLibrary, vm::setFilter, vm::setQuery, vm::toggle, vm::retry, vm::explainSimpler, vm::practiceSentence)
}

/** Grammar (design/grammar-calendar-settings.md): filters, search and the cards in a LazyColumn. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarContent(
    ui: GrammarUi,
    onMore: () -> Unit,
    onBack: () -> Unit,
    onLibrary: () -> Unit,
    onFilter: (GrammarFilter) -> Unit,
    onQuery: (String) -> Unit,
    onToggle: (Long) -> Unit,
    onRetry: () -> Unit,
    onSimpler: (Long) -> Unit = {},
    onPractice: (Long) -> Unit = {},
) {
    val c = JustStudyTheme.colors
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(scroll.nestedScrollConnection)) {
        JsTopBar("Grammar", "문법", "Explore Korean grammar with clear explanations and examples.", null, scroll, onMore = onMore, onBack = onBack)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Tokens.Space3).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
            GrammarFilter.entries.forEach { f -> FilterPill(f.name, f == ui.filter, { onFilter(f) }) }
        }
        JsTextField(
            ui.query, onQuery, Modifier.fillMaxWidth().padding(horizontal = Tokens.Space4, vertical = Tokens.Space2), placeholder = "Search in Korean or English",
            leading = { LucideIcon(Lucide.Search, contentDescription = null, size = 20.dp, tint = c.muted) },
            trailing = if (ui.query.isNotEmpty()) ({ IconButton({ onQuery("") }) { LucideIcon(Lucide.X, contentDescription = "Clear search", size = 20.dp, tint = c.muted) } }) else null,
        )
        Box(Modifier.fillMaxSize()) {
            when {
                ui.error -> Box(Modifier.padding(Tokens.Space4)) { ErrorState("Could not load grammar.", onRetry) }
                ui.points == null -> Box(Modifier.padding(Tokens.Space4)) { LoadingState("Loading grammar…") }
                !ui.any -> Box(Modifier.padding(Tokens.Space4)) {
                    EmptyState(Lucide.Languages, "No grammar in your notes yet.", null, null, action = "Open Library", onAction = onLibrary, actionColor = com.dero.juststudykorean.ui.components.StickerColor.Action)
                }
                ui.points.isEmpty() -> Text("Nothing matches \"${ui.query.trim().ifEmpty { ui.filter.name }}\".", Modifier.padding(Tokens.Space4), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = Tokens.Space4, end = Tokens.Space4, top = Tokens.Space2, bottom = Tokens.Space6),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
                ) {
                    items(ui.points, key = { it.id }) { p ->
                        val ai = if (ui.ai) GrammarCardAi(ui.written[p.id] ?: GrammarWritten(), { onSimpler(p.id) }, { onPractice(p.id) }) else null
                        GrammarCard(p, p.id == ui.open, { onToggle(p.id) }, ai = ai)
                    }
                }
            }
        }
    }
}
