package de.ingvoss.fotosortierer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.Formats
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.data.media.AccessLevel
import de.ingvoss.fotosortierer.ui.appContainer
import de.ingvoss.fotosortierer.ui.components.EmptyState
import de.ingvoss.fotosortierer.ui.components.ProgressSummary
import de.ingvoss.fotosortierer.ui.components.pluralText
import de.ingvoss.fotosortierer.ui.containerViewModel
import de.ingvoss.fotosortierer.ui.onboarding.openAppSettings
import de.ingvoss.fotosortierer.ui.theme.LocalFotoColors

/** Startseite: Weitermachen, Gesamtstatistik, Fortschritt der Hintergrundarbeiten, Hinweise zum Zugriff. */
@Composable
fun HomeScreen(
    onContinue: (MediaFilter, Boolean) -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenOnboarding: () -> Unit
) {
    val viewModel = containerViewModel { HomeViewModel(it) }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val labels = context.appContainer().labels
    val continueLabel by produceState(initialValue = "", ui.continueFilter) { value = labels.label(ui.continueFilter) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

            when (ui.access) {
                AccessLevel.NONE -> AccessCard(
                    message = stringResource(R.string.home_no_access),
                    actionLabel = stringResource(R.string.onboarding_allow),
                    onAction = onOpenOnboarding
                )
                AccessLevel.PARTIAL -> AccessCard(
                    message = stringResource(R.string.home_partial_access),
                    actionLabel = stringResource(R.string.onboarding_allow_all),
                    onAction = { openAppSettings(context) }
                )
                AccessLevel.FULL -> Unit
            }

            if (ui.access != AccessLevel.NONE && ui.overall.total == 0 && !ui.sync.running && ui.loaded) {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    message = stringResource(R.string.home_empty_message)
                )
            }

            if (ui.overall.total > 0) {
                ContinueCard(
                    label = continueLabel,
                    open = ui.continueCounts.open,
                    hasSession = ui.session != null,
                    onContinue = { onContinue(ui.continueFilter, ui.session != null) }
                )
                StatsCard(ui)
            }

            IndexingCard(ui)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenCalendar, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nav_calendar))
                }
                OutlinedButton(onClick = onOpenPlaces, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nav_places))
                }
                OutlinedButton(onClick = onOpenTrash, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nav_trash))
                }
            }
        }
    }
}

@Composable
private fun AccessCard(message: String, actionLabel: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun ContinueCard(label: String, open: Int, hasSession: Boolean, onContinue: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (hasSession) R.string.home_continue_session else R.string.home_start_sorting),
                style = MaterialTheme.typography.titleMedium
            )
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (open > 0) {
                Text(pluralText(R.plurals.open_count, open), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (hasSession) R.string.action_continue_sorting else R.string.action_start_sorting))
                }
            } else {
                Text(stringResource(R.string.home_all_done), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatsCard(ui: HomeUi) {
    val colors = LocalFotoColors.current
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_stats_title), style = MaterialTheme.typography.titleMedium)
            ProgressSummary(ui.overall, Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            StatRow(stringResource(R.string.status_keep), ui.overall.behalten.toString(), colors.keep)
            StatRow(
                stringResource(R.string.status_trash),
                stringResource(R.string.stat_count_size, ui.overall.papierkorb, Formats.bytes(ui.trashBytes)),
                colors.trash
            )
            StatRow(stringResource(R.string.status_deferred), ui.overall.zurueckgestellt.toString(), colors.deferred)
            StatRow(stringResource(R.string.status_unprocessed), ui.overall.unbearbeitet.toString(), colors.unprocessed)
            StatRow(stringResource(R.string.status_deleted), ui.overall.geloescht.toString(), colors.unprocessed)
            StatRow(stringResource(R.string.stat_freed), Formats.bytes(ui.freedBytes), colors.complete)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Fortschritt von Erfassung, Standortlesen und Ortsbenennung (Hintergrundarbeit). */
@Composable
private fun IndexingCard(ui: HomeUi) {
    val locating = ui.locationAccess && ui.locations.total > 0 && ui.locations.checked < ui.locations.total
    if (!ui.sync.running && !locating && ui.namesPending == 0 && ui.locationAccess) return
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_background_title), style = MaterialTheme.typography.titleMedium)
            if (ui.sync.running) {
                Text(stringResource(R.string.home_scanning, ui.sync.scanned), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (locating) {
                Text(
                    stringResource(R.string.home_locating, ui.locations.checked, ui.locations.total),
                    style = MaterialTheme.typography.bodyMedium
                )
                LinearProgressIndicator(
                    progress = { ui.locations.checked.toFloat() / ui.locations.total },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (ui.namesPending > 0) {
                Text(
                    pluralText(R.plurals.places_naming, ui.namesPending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!ui.locationAccess) {
                Text(
                    stringResource(R.string.home_no_location_access),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
