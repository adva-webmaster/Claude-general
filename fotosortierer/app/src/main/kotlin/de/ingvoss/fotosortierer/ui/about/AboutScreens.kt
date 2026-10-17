package de.ingvoss.fotosortierer.ui.about

import androidx.annotation.ArrayRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ingvoss.fotosortierer.BuildConfig
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.ui.components.AppTopBar

/** Ein Eintrag der Versionshinweise; die Punkte stehen als Textfeld in `res/values/release_notes.xml`. */
internal class ReleaseEntry(val version: String, val date: String, @ArrayRes val notes: Int)

/** Neueste zuerst. Bei jeder neuen Version hier und in `release_notes.xml` ergänzen. */
internal val RELEASES = listOf(
    ReleaseEntry("1.5.1", "17.10.2026", R.array.notes_1_5_1),
    ReleaseEntry("1.5.0", "17.10.2026", R.array.notes_1_5_0),
    ReleaseEntry("1.4.0", "17.10.2026", R.array.notes_1_4_0),
    ReleaseEntry("1.3.1", "17.10.2026", R.array.notes_1_3_1),
    ReleaseEntry("1.3.0", "17.10.2026", R.array.notes_1_3_0),
    ReleaseEntry("1.2.0", "17.10.2026", R.array.notes_1_2_0),
    ReleaseEntry("1.1.0", "13.10.2026", R.array.notes_1_1_0),
    ReleaseEntry("1.0.0", "11.10.2026", R.array.notes_1_0_0)
)

/** Versionshinweise mit dem Verlauf aller Versionen (neueste oben). */
@Composable
fun ReleaseNotesScreen(onBack: () -> Unit) {
    val resources = LocalResources.current
    Scaffold(topBar = { AppTopBar(stringResource(R.string.release_notes_title), onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(RELEASES, key = { it.version }) { release ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.release_notes_version, release.version),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    release.date,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (release.version == BuildConfig.VERSION_NAME) {
                                SuggestionChip(onClick = {}, label = { Text(stringResource(R.string.release_notes_installed)) })
                            }
                        }
                        resources.getStringArray(release.notes).forEach { note ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("•", style = MaterialTheme.typography.bodyMedium)
                                Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Lizenzen und Hinweise: VLC-Engine (LGPL 2.1) samt Quellen, weitere Bausteine, Volltexte der Lizenzen. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val resources = LocalResources.current
    var showLgpl by rememberSaveable { mutableStateOf(false) }
    var showApache by rememberSaveable { mutableStateOf(false) }
    val lgplText = remember { resources.openRawResource(R.raw.license_lgpl_2_1).bufferedReader().use { it.readText() } }
    val apacheText = remember { resources.openRawResource(R.raw.license_apache_2_0).bufferedReader().use { it.readText() } }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.licenses_title), onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(stringResource(R.string.licenses_intro), style = MaterialTheme.typography.bodyMedium)
            }
            item {
                LicenseCard(title = stringResource(R.string.licenses_vlc_title), text = stringResource(R.string.licenses_vlc_text)) {
                    LinkButton(stringResource(R.string.licenses_link_vlc_source)) { uriHandler.openUri(VLC_SOURCE_URL) }
                    LinkButton(stringResource(R.string.licenses_link_vlc_project)) { uriHandler.openUri(VLC_PROJECT_URL) }
                    LicenseToggle(
                        shown = showLgpl,
                        showLabel = stringResource(R.string.licenses_show_lgpl),
                        hideLabel = stringResource(R.string.licenses_hide_lgpl),
                        text = lgplText,
                        onToggle = { showLgpl = !showLgpl }
                    )
                }
            }
            item {
                LicenseCard(title = stringResource(R.string.licenses_others_title), text = stringResource(R.string.licenses_others_text)) {
                    LinkButton(stringResource(R.string.licenses_link_osm)) { uriHandler.openUri(OSM_COPYRIGHT_URL) }
                    LicenseToggle(
                        shown = showApache,
                        showLabel = stringResource(R.string.licenses_show_apache),
                        hideLabel = stringResource(R.string.licenses_hide_apache),
                        text = apacheText,
                        onToggle = { showApache = !showApache }
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseCard(title: String, text: String, actions: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }
}

@Composable
private fun LinkButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

/** Zeigt oder verbirgt den vollständigen Lizenztext (eine lange Textfläche in Festbreitenschrift). */
@Composable
private fun LicenseToggle(shown: Boolean, showLabel: String, hideLabel: String, text: String, onToggle: () -> Unit) {
    OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) { Text(if (shown) hideLabel else showLabel) }
    if (shown) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

private const val VLC_SOURCE_URL = "https://code.videolan.org/videolan/libvlcjni"
private const val VLC_PROJECT_URL = "https://www.videolan.org/"
private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
