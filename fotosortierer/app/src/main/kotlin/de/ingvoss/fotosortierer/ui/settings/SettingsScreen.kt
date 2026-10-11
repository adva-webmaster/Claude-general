package de.ingvoss.fotosortierer.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ingvoss.fotosortierer.BuildConfig
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.SwipeAction
import de.ingvoss.fotosortierer.core.SwipeDirection
import de.ingvoss.fotosortierer.data.settings.AppSettings
import de.ingvoss.fotosortierer.data.settings.ThemeMode
import de.ingvoss.fotosortierer.ui.components.AppTopBar
import de.ingvoss.fotosortierer.ui.components.ConfirmDialog
import de.ingvoss.fotosortierer.ui.containerViewModel
import de.ingvoss.fotosortierer.ui.onboarding.openAppSettings
import de.ingvoss.fotosortierer.ui.sort.actionLabel

@Composable
fun SettingsScreen() {
    val viewModel = containerViewModel { SettingsViewModel(it) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var albumDialog by remember { mutableStateOf(false) }
    var rebuildDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(context.getString(it.resId, *it.args.toTypedArray())) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_settings)) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SwipeSection(settings, viewModel)
                Section(stringResource(R.string.settings_section_order))
                SwitchRow(stringResource(R.string.settings_oldest_first), settings.oldestFirst, viewModel::setOldestFirst)

                Section(stringResource(R.string.settings_section_places))
                Text(stringResource(R.string.settings_radius_hint), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppSettings.RADIUS_CHOICES.forEach { meters ->
                        FilterChip(
                            selected = settings.clusterRadiusMeters == meters,
                            onClick = { viewModel.setClusterRadius(meters) },
                            label = { Text(radiusLabel(meters)) }
                        )
                    }
                }

                Section(stringResource(R.string.settings_section_media))
                SwitchRow(stringResource(R.string.kind_photos), settings.showPhotos, viewModel::setShowPhotos)
                SwitchRow(stringResource(R.string.kind_videos), settings.showVideos, viewModel::setShowVideos)
                SwitchRow(stringResource(R.string.kind_screenshots), settings.showScreenshots, viewModel::setShowScreenshots)
                OutlinedButton(onClick = { albumDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_excluded_albums, settings.excludedAlbums.size))
                }

                Section(stringResource(R.string.settings_section_appearance))
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = settings.themeMode == mode, role = Role.RadioButton) { viewModel.setTheme(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = settings.themeMode == mode, onClick = null)
                        Text(themeLabel(mode), modifier = Modifier.padding(start = 12.dp))
                    }
                }

                Section(stringResource(R.string.settings_section_behavior))
                SwitchRow(stringResource(R.string.settings_haptics), settings.haptics, viewModel::setHaptics)
                SwitchRow(stringResource(R.string.settings_video_sound), settings.videoSoundDefault, viewModel::setVideoSound)
                SwitchRow(stringResource(R.string.settings_info_line), settings.showInfoLine, viewModel::setInfoLine)

                Section(stringResource(R.string.settings_section_data))
                OutlinedButton(onClick = { exportLauncher.launch("fotosortierer-sicherung.json") }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_export))
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_import))
                }
                Text(stringResource(R.string.settings_backup_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { rebuildDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_rebuild))
                }
                OutlinedButton(onClick = { openAppSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_system_permissions))
                }

                Section(stringResource(R.string.settings_section_about))
                Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.settings_about_privacy), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.settings_about_osm), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (albumDialog) {
        AlbumDialog(
            albums = albums.map { it.groupKey to it.total },
            excluded = settings.excludedAlbums,
            onDismiss = { albumDialog = false },
            onConfirm = { viewModel.setExcludedAlbums(it); albumDialog = false }
        )
    }
    if (rebuildDialog) {
        ConfirmDialog(
            title = stringResource(R.string.settings_rebuild),
            message = stringResource(R.string.settings_rebuild_message),
            confirmLabel = stringResource(R.string.settings_rebuild_confirm),
            onConfirm = { rebuildDialog = false; viewModel.rebuildDatabase() },
            onDismiss = { rebuildDialog = false }
        )
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SwipeSection(settings: AppSettings, viewModel: SettingsViewModel) {
    Section(stringResource(R.string.settings_section_swipe))
    Text(stringResource(R.string.settings_swipe_hint), style = MaterialTheme.typography.bodySmall)
    val mapping = settings.swipeMapping
    listOf(
        SwipeDirection.LINKS to R.string.settings_swipe_left,
        SwipeDirection.RECHTS to R.string.settings_swipe_right,
        SwipeDirection.OBEN to R.string.settings_swipe_up
    ).forEach { (direction, labelRes) ->
        Column(Modifier.padding(top = 6.dp)) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SwipeAction.entries.forEach { action ->
                    FilterChip(
                        selected = mapping.actionFor(direction) == action,
                        onClick = { viewModel.assignSwipe(direction, action) },
                        label = { Text(actionLabel(action)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun radiusLabel(meters: Int): String =
    if (meters >= 1000) stringResource(R.string.unit_km, meters / 1000) else stringResource(R.string.unit_m, meters)

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)

@Composable
private fun AlbumDialog(
    albums: List<Pair<String, Int>>,
    excluded: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var selection by remember { mutableStateOf(excluded) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_albums_title)) },
        text = {
            if (albums.isEmpty()) {
                Text(stringResource(R.string.settings_albums_none))
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(albums, key = { it.first }) { (name, count) ->
                        val checked = name in selection
                        Row(
                            Modifier.fillMaxWidth()
                                .toggleable(value = checked, role = Role.Checkbox) { selection = if (checked) selection - name else selection + name }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text("$name ($count)", modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selection) }) { Text(stringResource(R.string.action_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
