package de.ingvoss.fotosortierer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.Formats
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.core.MediaStatus
import de.ingvoss.fotosortierer.data.media.AccessLevel
import de.ingvoss.fotosortierer.ui.appContainer
import de.ingvoss.fotosortierer.ui.components.EmptyState
import de.ingvoss.fotosortierer.ui.components.ProgressSummary
import de.ingvoss.fotosortierer.ui.components.pluralText
import de.ingvoss.fotosortierer.ui.containerViewModel
import de.ingvoss.fotosortierer.ui.onboarding.openAppSettings
import de.ingvoss.fotosortierer.ui.theme.AppIcons
import de.ingvoss.fotosortierer.ui.theme.LocalFotoColors
import de.ingvoss.fotosortierer.ui.theme.brandGradient

/** Startseite: Weitermachen, Gesamtstatistik, Fortschritt der Hintergrundarbeiten, Hinweise zum Zugriff. */
@Composable
fun HomeScreen(
    onContinue: (MediaFilter, Boolean) -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenFilter: (MediaFilter) -> Unit
) {
    val viewModel = containerViewModel { HomeViewModel(it) }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val labels = context.appContainer().labels
    val continueLabel by produceState(initialValue = "", ui.continueFilter) { value = labels.label(ui.continueFilter) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

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
                StatsCard(ui, onOpenFilter)
            }

            IndexingCard(ui)
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

/** Kopfbereich in den Markenfarben: Weitermachen bzw. Sortieren starten, mit der wichtigsten Aktion. */
@Composable
private fun ContinueCard(label: String, open: Int, hasSession: Boolean, onContinue: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(brandGradient())
            .padding(22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (hasSession) R.string.home_continue_session else R.string.home_start_sorting),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            if (open > 0) {
                Text(
                    pluralText(R.plurals.open_count, open),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF8E1B20))
                ) {
                    Text(stringResource(if (hasSession) R.string.action_continue_sorting else R.string.action_start_sorting))
                }
            } else {
                Text(stringResource(R.string.home_all_done), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }
    }
}

@Composable
private fun StatsCard(ui: HomeUi, onOpenFilter: (MediaFilter) -> Unit) {
    val colors = LocalFotoColors.current
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_stats_title), style = MaterialTheme.typography.titleMedium)
            ProgressSummary(ui.overall, Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            // Tipp auf eine Zeile zeigt diese Medien (und erlaubt dort das erneute Sortieren).
            fun show(status: MediaStatus) = { onOpenFilter(MediaFilter(statuses = setOf(status))) }
            StatRow(stringResource(R.string.status_keep), ui.overall.behalten.toString(), colors.keep, show(MediaStatus.BEHALTEN))
            StatRow(
                stringResource(R.string.status_trash),
                stringResource(R.string.stat_count_size, ui.overall.papierkorb, Formats.bytes(ui.trashBytes)),
                colors.trash,
                show(MediaStatus.PAPIERKORB)
            )
            StatRow(stringResource(R.string.status_deferred), ui.overall.zurueckgestellt.toString(), colors.deferred, show(MediaStatus.ZURUECKGESTELLT))
            StatRow(stringResource(R.string.status_unprocessed), ui.overall.unbearbeitet.toString(), colors.unprocessed, show(MediaStatus.UNBEARBEITET))
            StatRow(stringResource(R.string.status_deleted), ui.overall.geloescht.toString(), colors.unprocessed)
            StatRow(stringResource(R.string.stat_freed), Formats.bytes(ui.freedBytes), colors.complete)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyMedium)
            if (onClick != null) Icon(AppIcons.Chevron, contentDescription = null, modifier = Modifier.size(18.dp))
        }
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
