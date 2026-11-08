package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.navigation.MoreDestination
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/** More: Grammar, Progress, Calendar · divider · Dark mode with ThemeSwitch · Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(darkMode: Boolean, onDarkMode: (Boolean) -> Unit, onOpen: (MoreDestination) -> Unit, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(topStart = Tokens.RadiusLg, topEnd = Tokens.RadiusLg)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(Tokens.Border, JustStudyTheme.colors.line, shape),
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.scrim, // the token already carries its alpha
        dragHandle = { BottomSheetDefaults.DragHandle(width = 32.dp, height = 4.dp) },
    ) {
        MoreSheetContent(darkMode, onDarkMode, onOpen)
    }
}

/** The rows of MoreSheet, without the sheet (screenshot tests render this). */
@Composable
internal fun MoreSheetContent(darkMode: Boolean, onDarkMode: (Boolean) -> Unit, onOpen: (MoreDestination) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = Tokens.Space2)) {
        MoreRow(Lucide.Languages, "Grammar", "문법") { onOpen(MoreDestination.Grammar) }
        MoreRow(Lucide.TrendingUp, "Progress", "진도") { onOpen(MoreDestination.Progress) }
        MoreRow(Lucide.CalendarDays, "Calendar", "달력") { onOpen(MoreDestination.Calendar) }
        HorizontalDivider(Modifier.padding(vertical = Tokens.Space2), thickness = Tokens.Border, color = JustStudyTheme.colors.line)
        var moonTaps by remember { mutableIntStateOf(0) }
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .toggleable(darkMode, role = Role.Switch) { moonTaps++; onDarkMode(it) } // the whole row toggles
                .padding(horizontal = Tokens.Space6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LucideIcon(Lucide.Moon, contentDescription = null, tint = JustStudyTheme.colors.control, animateKey = moonTaps)
            Spacer(Modifier.width(Tokens.Space4))
            Text("Dark mode", Modifier.weight(1f), style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
            ThemeSwitch(checked = darkMode, onCheckedChange = null)
        }
        MoreRow(Lucide.Settings, "Settings", "설정") { onOpen(MoreDestination.Settings) }
    }
}

@Composable
private fun MoreRow(icon: Lucide, name: String, ko: String, onClick: () -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { taps++; onClick() }
            .padding(horizontal = Tokens.Space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(icon, contentDescription = null, tint = JustStudyTheme.colors.control, animateKey = taps)
        Spacer(Modifier.width(Tokens.Space4))
        Text(name, style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
        KoSide(ko, JustStudyTheme.korean.bodyBold.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
