package com.dero.juststudykorean.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.data.prefs.ThemeMode
import com.dero.juststudykorean.ui.components.ErrorState
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.SelectedGroup
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens
import java.time.LocalDate

private val BACKUP_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

/** Settings (design/shell.md): Theme, Import backup and Export backup. Files only through SAF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onMore: () -> Unit, onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    var pending by rememberSaveable { mutableStateOf<String?>(null) } // chosen file, waiting for confirmation
    var importTaps by remember { mutableIntStateOf(0) }
    var exportTaps by remember { mutableIntStateOf(0) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pending = uri?.toString() }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(vm::export) }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
            JsTopBar("Settings", "설정", null, null, scroll, onMore = onMore, onBack = onBack)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SettingsRow(Lucide.Moon, "Theme", "System follows your phone.", animateKey = theme)
                SelectedGroup(
                    options = ThemeMode.entries.map { it.name },
                    selected = theme.ordinal,
                    onSelect = { vm.setTheme(ThemeMode.entries[it]) },
                    modifier = Modifier.fillMaxWidth().padding(start = Tokens.Space4, end = Tokens.Space4, bottom = Tokens.Space2),
                )
                SettingsRow(Lucide.Download, "Import backup", "Replace everything with a .jsbackup.zip.", animateKey = importTaps, enabled = !busy) {
                    importTaps++
                    pick.launch(BACKUP_TYPES)
                }
                SettingsRow(Lucide.Download, "Export backup", "Save everything to a .jsbackup.zip.", animateKey = exportTaps, iconRotation = 180f, enabled = !busy) {
                    exportTaps++
                    save.launch("juststudy-backup-${LocalDate.now()}.jsbackup.zip")
                }
                Column(Modifier.padding(horizontal = Tokens.Space4), verticalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = JustStudyTheme.colors.brand, trackColor = JustStudyTheme.colors.tint, gapSize = 0.dp)
                    error?.let { ErrorState(it, onRetry = { pick.launch(BACKUP_TYPES) }, title = "Couldn't finish") }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    pending?.let { uri ->
        AlertDialog(
            onDismissRequest = { pending = null },
            text = { Text("This replaces everything in the app with the backup. Continue?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { StickerButton("Import", { pending = null; vm.import(android.net.Uri.parse(uri)) }, color = StickerColor.Danger) },
            dismissButton = { StickerButton("Cancel", { pending = null }, color = StickerColor.Gray) },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

/** A Settings row, like MoreSheet's: 56 dp, `control` icon, `body-bold` name and one `muted` line. */
@Composable
private fun SettingsRow(
    icon: Lucide,
    title: String,
    line: String,
    animateKey: Any?,
    iconRotation: Float = 0f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = Tokens.Space6, vertical = Tokens.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(icon, contentDescription = null, modifier = Modifier.rotate(iconRotation), tint = JustStudyTheme.colors.control, animateKey = animateKey)
        Spacer(Modifier.width(Tokens.Space4))
        Column {
            Text(title, style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
