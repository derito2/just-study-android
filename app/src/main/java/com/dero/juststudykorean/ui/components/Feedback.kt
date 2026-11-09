package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/** Snackbar (design/shell.md): `tooltip` background, white text, `on-tooltip-action` action. */
@Composable
fun JsSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        val colors = JustStudyTheme.colors
        Snackbar(data, containerColor = colors.tooltip, contentColor = Color.White, actionColor = colors.onTooltipAction)
    }
}

/**
 * A centered dialog: Card with 24 dp padding over the `scrim`; [title] with its Korean, [message] in `muted`,
 * [content] and the two buttons side by side.
 */
@Composable
fun JsDialog(
    title: String?,
    ko: String?,
    message: String?,
    onDismiss: () -> Unit,
    confirm: @Composable (Modifier) -> Unit,
    dismiss: @Composable (Modifier) -> Unit,
    content: @Composable () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .border(Tokens.Border, JustStudyTheme.colors.line, MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .padding(Tokens.Space6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (title != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(title, Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    if (ko != null) KoSide(ko, MaterialTheme.typography.titleMedium.fontSize, Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(Tokens.Space3))
            }
            if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium, color = JustStudyTheme.colors.muted, textAlign = TextAlign.Center)
            content()
            Spacer(Modifier.height(Tokens.Space4))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
                dismiss(Modifier.weight(1f))
                confirm(Modifier.weight(1f))
            }
        }
    }
}
