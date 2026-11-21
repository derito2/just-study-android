package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.Tokens

/** design/polish.md P-02: the top of a sheet: the handle (32 × 4 dp, 12 dp above and below) and, if it has one, the title with its Korean. */
@Composable
fun SheetHeader(title: String? = null, ko: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp).size(32.dp, 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
        )
        if (title != null) {
            Row(Modifier.padding(start = Tokens.Space6, end = Tokens.Space6, bottom = 12.dp).semantics { heading() }, verticalAlignment = Alignment.Bottom) {
                Text(title, Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (ko != null) KoSide(ko, MaterialTheme.typography.titleMedium.fontSize, Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
