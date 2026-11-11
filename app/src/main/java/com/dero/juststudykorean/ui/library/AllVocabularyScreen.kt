package com.dero.juststudykorean.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.ui.components.ErrorState
import com.dero.juststudykorean.ui.components.JsTextField
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.LoadingState
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

@Composable
fun AllVocabularyScreen(onBack: () -> Unit, vm: AllVocabularyViewModel = hiltViewModel()) {
    val state by vm.words.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    AllVocabularyContent(state, query, onBack, vm::setQuery, vm::retry)
}

/** All vocabulary (design/library.md C): a LazyColumn of 48 dp rows and the search fixed above the keyboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllVocabularyContent(state: VocabularyState, query: String, onBack: () -> Unit, onQuery: (String) -> Unit, onRetry: () -> Unit) {
    val c = JustStudyTheme.colors
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(scroll.nestedScrollConnection)) {
        JsTopBar("All vocabulary", "모든 단어", null, null, scroll, onMore = null, onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val words = state.words
            when {
                state.failed -> Box(Modifier.padding(Tokens.Space4)) { ErrorState("Could not load your vocabulary.", onRetry) }
                words == null -> Box(Modifier.padding(Tokens.Space4)) { LoadingState("Loading…") }
                words.isEmpty() && state.query.isBlank() -> Message("No vocabulary in your documents yet.")
                words.isEmpty() -> Message("Nothing matches \"${state.query}\".")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(words, key = { it.korean }) { w ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = Tokens.Space4), verticalAlignment = Alignment.CenterVertically) {
                            Text(w.korean, style = JustStudyTheme.korean.koBody.copy(fontWeight = FontWeight.Bold), color = c.ink)
                            Spacer(Modifier.width(Tokens.Space3))
                            Text(w.english, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.End)
                        }
                        HorizontalDivider(color = c.line)
                    }
                }
            }
        }
        JsTextField(
            query, onQuery, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()
                .padding(horizontal = Tokens.Space4, vertical = Tokens.Space2),
            placeholder = "Search in Korean or English",
            leading = { LucideIcon(Lucide.Search, contentDescription = null, size = 20.dp, tint = c.muted) },
            trailing = if (query.isNotEmpty()) ({ IconButton({ onQuery("") }) { LucideIcon(Lucide.X, contentDescription = "Clear search", size = 20.dp, tint = c.muted) } }) else null,
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(text, Modifier.padding(Tokens.Space4), style = MaterialTheme.typography.bodyMedium, color = JustStudyTheme.colors.muted)
}
