package com.dero.juststudykorean.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.data.grammar.GrammarPointUi
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.Tokens

/**
 * GrammarCard (design/grammar-calendar-settings.md): closed, the pattern (18 sp, 800) and the meaning in one line;
 * open, `tint` with `tint-border` (dark: `line`) and Meaning · Structure · Explanation · Examples · Notes.
 */
@Composable
fun GrammarCard(point: GrammarPointUi, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, dark: Boolean = isSystemInDarkTheme()) {
    val c = JustStudyTheme.colors
    val ink = MaterialTheme.colorScheme.onSurface
    val turn by animateFloatAsState(if (open) -90f else 0f, tween(Motion.Base), label = "chevron")
    Column(
        modifier.fillMaxWidth()
            .border(Tokens.Border, if (open) (if (dark) c.line else c.tintBorder) else c.line, MaterialTheme.shapes.large)
            .background(if (open) c.tint else MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (open) "Open" else "Closed" }
            .padding(Tokens.Space4)
            .animateContentSize(tween(Motion.Base)),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(point.pattern, style = JustStudyTheme.korean.koTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = ink)
                Text(point.meaning, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = if (open) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
            }
            LucideIcon(Lucide.ChevronLeft, contentDescription = null, size = 20.dp, tint = c.muted, modifier = Modifier.rotate(turn))
        }
        if (open) {
            val ko = JustStudyTheme.korean.koBody
            @Composable
            fun section(title: String, body: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = JustStudyTheme.korean.bodyBold, color = ink)
                body()
            }
            section("Meaning") { Text(point.meaning, style = MaterialTheme.typography.bodyMedium, color = ink) }
            if (point.structure.isNotEmpty()) section("Structure") { point.structure.forEach { Text("• $it", style = ko, color = ink) } }
            if (point.explanation.isNotEmpty()) section("Explanation") { point.explanation.forEach { Text(it, style = ko, color = ink) } }
            if (point.examples.isNotEmpty()) section("Examples") {
                point.examples.forEach { (k, e) ->
                    Text(k, style = ko, color = ink)
                    if (e.isNotEmpty()) Text(e, Modifier.padding(bottom = 2.dp), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
            }
            if (point.notes.isNotEmpty()) section("Notes") { point.notes.forEach { Text(it, style = ko, color = ink) } }
        }
    }
}
