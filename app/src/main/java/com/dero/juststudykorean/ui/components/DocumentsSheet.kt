package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/** A bottom sheet like MoreSheet: `surface`, radius 16 on top, `line` border and a 32 × 4 dp handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(topStart = Tokens.RadiusLg, topEnd = Tokens.RadiusLg)
    ModalBottomSheet(
        onDismissRequest = onDismiss, shape = shape, containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.scrim, dragHandle = null, contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(Modifier.border(Tokens.Border, JustStudyTheme.colors.line, shape).navigationBarsPadding().padding(bottom = Tokens.Space4)) {
            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally), width = 32.dp, height = 4.dp)
            Column(Modifier.verticalScroll(rememberScrollState())) { content() }
        }
    }
}

/** The Documents selector field: "All documents" (or how many) and `chevron-left` turned −90°. */
@Composable
fun DocumentsField(selected: Set<Long>, documents: List<DocumentsEntity>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = when {
        selected.isEmpty() -> "All documents"
        selected.size == 1 -> documents.firstOrNull { it.id in selected }?.originalName ?: "1 document"
        else -> "${selected.size} documents"
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .border(Tokens.Border, JustStudyTheme.colors.line, MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = "Choose documents", onClick = onClick)
            .padding(horizontal = Tokens.Space4, vertical = Tokens.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        LucideIcon(Lucide.ChevronLeft, contentDescription = null, size = 20.dp, tint = JustStudyTheme.colors.muted, modifier = Modifier.rotate(-90f))
    }
}

/** One checkbox per processed document, plus All documents (an empty selection means all). */
@Composable
fun DocumentsSheet(documents: List<DocumentsEntity>, selected: Set<Long>, onChange: (Set<Long>) -> Unit, onDismiss: () -> Unit) {
    JsSheet(onDismiss) {
        Text("Documents", Modifier.padding(horizontal = Tokens.Space6, vertical = Tokens.Space2), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        CheckRow("All documents", selected.isEmpty()) { onChange(emptySet()) }
        documents.forEach { d -> CheckRow(d.originalName, d.id in selected) { onChange(if (d.id in selected) selected - d.id else selected + d.id) } }
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox, onClick = onClick).padding(horizontal = Tokens.Space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JsCheckbox(checked)
        Spacer(Modifier.width(Tokens.Space3))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
