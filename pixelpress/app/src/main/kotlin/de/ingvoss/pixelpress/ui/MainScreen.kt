package de.ingvoss.pixelpress.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.ingvoss.pixelpress.ConversionController
import de.ingvoss.pixelpress.ItemState
import de.ingvoss.pixelpress.JobItem
import de.ingvoss.pixelpress.Options
import de.ingvoss.pixelpress.R
import de.ingvoss.pixelpress.UiState
import de.ingvoss.pixelpress.core.ConversionMode
import de.ingvoss.pixelpress.core.Formats
import de.ingvoss.pixelpress.core.PathMapper
import de.ingvoss.pixelpress.core.Quality
import kotlinx.coroutines.delay

/** Wurzel: Hauptseite oder Info-Seite (Versionshinweise, Lizenzen). */
@Composable
fun PixelPressRoot(controller: ConversionController) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    if (showInfo) {
        BackHandler { showInfo = false }
        InfoScreen(onBack = { showInfo = false })
    } else {
        MainScreen(controller, onInfo = { showInfo = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(controller: ConversionController, onInfo: () -> Unit) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    // Die Einstellungsseite für "Zugriff auf alle Dateien" meldet nichts zurück: Solange er fehlt, regelmäßig nachsehen.
    LaunchedEffect(hasAccess) {
        while (!hasAccess) {
            delay(ACCESS_POLL_MS)
            hasAccess = Environment.isExternalStorageManager()
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { controller.start() }
    val directoryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val path = PathMapper.treeDocumentIdToPath(
                DocumentsContract.getTreeDocumentId(uri), Environment.getExternalStorageDirectory().path
            )
            if (path == null) {
                Toast.makeText(context, R.string.error_directory_unsupported, Toast.LENGTH_LONG).show()
            } else {
                controller.setDirectory(path)
            }
        }
    }
    val startConversion: () -> Unit = {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else controller.start()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = { TextButton(onClick = onInfo) { Text(stringResource(R.string.action_info)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = { ActionBar(state, hasAccess, onStart = startConversion, onCancel = controller::cancel) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!hasAccess) {
                item("access") {
                    AccessCard {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                        )
                    }
                }
            }
            item("directory") {
                DirectoryCard(state, enabled = !state.running) { directoryLauncher.launch(null) }
            }
            item("options") {
                OptionsCard(state.options, enabled = !state.running, onChange = controller::updateOptions)
            }
            if (state.error != null) {
                item("error") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(state.error ?: "", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            if (state.finished) {
                item("summary") { SummaryCard(state, onDelete = controller::deleteOriginals) }
            }
            if (state.items.isNotEmpty()) {
                item("listHeader") { ListHeader(state, onSelectAll = controller::selectAll) }
            }
            itemsIndexed(state.items, key = { _, item -> item.job.source.path }) { index, item ->
                FileRow(item, state.directory, canSelect = !state.running) { controller.toggleSelected(index) }
            }
        }
    }

    if (state.offerDeletion) {
        val verified = state.items.filter { it.verified && !it.originalDeleted }
        AlertDialog(
            onDismissRequest = controller::dismissDeletionOffer,
            title = { Text(stringResource(R.string.delete_title)) },
            text = {
                Text(stringResource(R.string.delete_message, verified.size, Formats.bytes(verified.sumOf { it.job.source.sizeBytes })))
            },
            confirmButton = { TextButton(onClick = controller::deleteOriginals) { Text(stringResource(R.string.delete_confirm)) } },
            dismissButton = { TextButton(onClick = controller::dismissDeletionOffer) { Text(stringResource(R.string.delete_keep)) } }
        )
    }
}

@Composable
private fun AccessCard(onGrant: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.access_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(stringResource(R.string.access_message), color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onGrant) { Text(stringResource(R.string.access_button)) }
        }
    }
}

@Composable
private fun DirectoryCard(state: UiState, enabled: Boolean, onChoose: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.directory_title), style = MaterialTheme.typography.titleMedium)
            Text(
                state.directory ?: stringResource(R.string.directory_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.scanning) {
                Text(stringResource(R.string.directory_scanning), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (state.directory != null) {
                val found = state.items.size
                val skipped = state.items.count { it.state == ItemState.SKIPPED }
                Text(
                    stringResource(R.string.directory_found, found, Formats.bytes(state.items.sumOf { it.job.source.sizeBytes }), skipped),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onChoose, enabled = enabled) { Text(stringResource(R.string.directory_choose)) }
        }
    }
}

@Composable
private fun OptionsCard(options: Options, enabled: Boolean, onChange: ((Options) -> Options) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.options_title), style = MaterialTheme.typography.titleMedium)
            SettingSwitch(stringResource(R.string.option_recursive), null, options.recursive, enabled) { v -> onChange { it.copy(recursive = v) } }
            SettingSwitch(stringResource(R.string.option_skip_existing), stringResource(R.string.option_skip_existing_info), options.skipExisting, enabled) { v ->
                onChange { it.copy(skipExisting = v) }
            }
            SettingSwitch(stringResource(R.string.option_copy), stringResource(R.string.option_copy_info), options.copyWhenPossible, enabled) { v ->
                onChange { it.copy(copyWhenPossible = v) }
            }
            Text(stringResource(R.string.option_quality), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Quality.entries.forEach { quality ->
                    FilterChip(
                        selected = options.quality == quality,
                        onClick = { onChange { it.copy(quality = quality) } },
                        enabled = enabled,
                        label = { Text(stringResource(qualityLabel(quality))) }
                    )
                }
            }
            SettingSwitch(
                stringResource(R.string.option_delete), stringResource(R.string.option_delete_info), options.deleteOriginals, enabled
            ) { v -> onChange { it.copy(deleteOriginals = v) } }
        }
    }
}

private fun qualityLabel(quality: Quality): Int = when (quality) {
    Quality.FAST -> R.string.quality_fast
    Quality.BALANCED -> R.string.quality_balanced
    Quality.HIGH -> R.string.quality_high
}

@Composable
private fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SummaryCard(state: UiState, onDelete: () -> Unit) {
    val done = state.items.count { it.state == ItemState.DONE }
    val failed = state.items.count { it.state == ItemState.FAILED }
    val cancelled = state.items.count { it.state == ItemState.CANCELLED }
    val deletable = state.items.filter { it.verified && !it.originalDeleted }
    val deleted = state.items.count { it.originalDeleted }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(stringResource(R.string.summary_counts, done, failed, cancelled), color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (deleted > 0) Text(stringResource(R.string.summary_deleted, deleted), color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (deletable.isNotEmpty() && !state.running) {
                OutlinedButton(onClick = onDelete) {
                    Text(stringResource(R.string.summary_delete_button, deletable.size, Formats.bytes(deletable.sumOf { it.job.source.sizeBytes })))
                }
            }
        }
    }
}

@Composable
private fun ListHeader(state: UiState, onSelectAll: (Boolean) -> Unit) {
    val selectable = state.items.filter { it.state != ItemState.SKIPPED && it.state != ItemState.DONE }
    val selected = selectable.count { it.selected }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.list_selected, selected, selectable.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        if (!state.running) {
            TextButton(onClick = { onSelectAll(selected < selectable.size) }) {
                Text(stringResource(if (selected < selectable.size) R.string.list_select_all else R.string.list_select_none))
            }
        }
    }
}

@Composable
private fun FileRow(item: JobItem, directory: String?, canSelect: Boolean, onToggle: () -> Unit) {
    val relative = directory?.let { item.job.source.path.removePrefix("$it/") } ?: item.job.source.path
    val selectable = canSelect && item.state != ItemState.SKIPPED && item.state != ItemState.DONE
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().then(if (selectable) Modifier.clickable(onClick = onToggle) else Modifier).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = item.selected, onCheckedChange = { onToggle() }, enabled = selectable)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(relative, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(statusText(item), style = MaterialTheme.typography.bodySmall, color = statusColor(item))
                if (item.state == ItemState.RUNNING) {
                    val progress = item.progress
                    if (progress == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun statusColor(item: JobItem) = when (item.state) {
    ItemState.FAILED -> MaterialTheme.colorScheme.error
    ItemState.DONE -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun statusText(item: JobItem): String {
    val size = Formats.bytes(item.job.source.sizeBytes)
    return when (item.state) {
        ItemState.WAITING -> stringResource(R.string.state_waiting, size)
        ItemState.SKIPPED -> item.message ?: size
        ItemState.RUNNING -> {
            val mode = stringResource(if (item.mode == ConversionMode.COPY) R.string.mode_copy else R.string.mode_transcode)
            val percent = item.progress?.let { stringResource(R.string.state_percent, (it * 100).toInt()) }
            listOfNotNull(size, mode, percent).joinToString(" · ")
        }
        ItemState.DONE -> {
            val ratio = item.verdict?.ratio?.let { (it * 100).toInt() } ?: 0
            val base = stringResource(R.string.state_done, size, Formats.bytes(item.outputBytes), ratio)
            if (item.originalDeleted) base + " · " + stringResource(R.string.state_original_deleted) else base
        }
        ItemState.FAILED -> stringResource(R.string.state_failed, item.message ?: "")
        ItemState.CANCELLED -> stringResource(R.string.state_cancelled)
    }
}

@Composable
private fun ActionBar(state: UiState, hasAccess: Boolean, onStart: () -> Unit, onCancel: () -> Unit) {
    val selected = state.items.count { it.selected && it.state != ItemState.SKIPPED && it.state != ItemState.DONE }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.preparing) {
                Text(stringResource(R.string.bar_preparing), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (state.running) {
                val active = state.items.filter { it.selected && it.state != ItemState.SKIPPED && it.state != ItemState.DONE || it.state == ItemState.RUNNING }
                val finished = state.items.count { it.state == ItemState.DONE || it.state == ItemState.FAILED }
                val total = (finished + active.count { it.state == ItemState.WAITING || it.state == ItemState.RUNNING }).coerceAtLeast(1)
                val current = state.items.firstOrNull { it.state == ItemState.RUNNING }?.progress ?: 0f
                Text(stringResource(R.string.bar_progress, finished + 1, total), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { ((finished + current) / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
            if (state.running || state.preparing) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
            } else {
                Button(onClick = onStart, enabled = hasAccess && selected > 0, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_start, selected))
                }
            }
        }
    }
}

private const val ACCESS_POLL_MS = 1000L
