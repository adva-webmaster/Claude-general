package de.ingvoss.pixelpress.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ingvoss.pixelpress.BuildConfig
import de.ingvoss.pixelpress.R

/** Info: Version, Versionshinweise mit Verlauf, Lizenzen (FFmpeg steht unter GPL v3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val resources = LocalResources.current
    var showLicense by rememberSaveable { mutableStateOf(false) }
    val licenseText = remember { resources.openRawResource(R.raw.license_gpl_3).bufferedReader().use { it.readText() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.info_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(stringResource(R.string.info_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.info_intro), style = MaterialTheme.typography.bodyMedium)
            }
            item {
                InfoCard(stringResource(R.string.info_notes_title)) {
                    listOf(
                        "1.0.1" to R.array.release_notes_1_0_1,
                        "1.0.0" to R.array.release_notes_1_0_0
                    ).forEach { (version, notes) ->
                        Text(version, style = MaterialTheme.typography.titleSmall)
                        resources.getStringArray(notes).forEach { note ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("•")
                                Text(note, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            item {
                InfoCard(stringResource(R.string.info_license_title)) {
                    Text(stringResource(R.string.info_license_text), style = MaterialTheme.typography.bodyMedium)
                    LinkButton(stringResource(R.string.info_link_ffmpeg)) { uriHandler.openUri("https://ffmpeg.org/") }
                    LinkButton(stringResource(R.string.info_link_x264)) { uriHandler.openUri("https://www.videolan.org/developers/x264.html") }
                    LinkButton(stringResource(R.string.info_link_termux)) { uriHandler.openUri("https://github.com/termux/termux-packages") }
                    LinkButton(stringResource(R.string.info_link_package)) { uriHandler.openUri("https://github.com/yausername/youtubedl-android") }
                    OutlinedButton(onClick = { showLicense = !showLicense }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (showLicense) R.string.info_hide_gpl else R.string.info_show_gpl))
                    }
                    if (showLicense) {
                        Text(licenseText, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun LinkButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}
