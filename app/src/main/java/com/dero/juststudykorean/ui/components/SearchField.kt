package com.dero.juststudykorean.ui.components

import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.JustStudyTheme

/** design/polish.md P-02: the one search field (Grammar, All vocabulary): a search icon and, with text, a 48 dp Clear search. */
@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search in Korean or English") {
    val c = JustStudyTheme.colors
    JsTextField(
        query, onQuery, modifier, placeholder = placeholder,
        leading = { LucideIcon(Lucide.Search, contentDescription = null, size = 20.dp, tint = c.muted) },
        trailing = if (query.isNotEmpty()) ({ IconButton({ onQuery("") }) { LucideIcon(Lucide.X, contentDescription = "Clear search", size = 20.dp, tint = c.muted) } }) else null,
    )
}
