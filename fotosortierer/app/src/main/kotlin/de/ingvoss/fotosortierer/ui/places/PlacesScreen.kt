package de.ingvoss.fotosortierer.ui.places

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.CaptureTime
import de.ingvoss.fotosortierer.core.FilterCodec
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.core.StatusCounts
import de.ingvoss.fotosortierer.ui.appContainer
import de.ingvoss.fotosortierer.ui.components.AppTopBar
import de.ingvoss.fotosortierer.ui.components.EmptyState
import de.ingvoss.fotosortierer.ui.components.ProgressSummary
import de.ingvoss.fotosortierer.ui.components.ScopeChip
import de.ingvoss.fotosortierer.ui.components.StateMark
import de.ingvoss.fotosortierer.ui.components.pluralText
import de.ingvoss.fotosortierer.ui.containerViewModel
import de.ingvoss.fotosortierer.ui.theme.AppIcons
import java.time.ZoneId

/** Orte: Karte mit Clustern und Liste (sortierbar) mit dem Eintrag "Ohne Ort". */
@Composable
fun PlacesScreen(
    scope: MediaFilter,
    onOpenFilter: (MediaFilter) -> Unit,
    onStartSorting: (MediaFilter) -> Unit,
    onShowCalendar: (MediaFilter) -> Unit,
    onClearScope: () -> Unit
) {
    val viewModel = containerViewModel(key = "places-" + FilterCodec.encode(scope)) { PlacesViewModel(it, scope) }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sort by rememberSaveable { mutableStateOf(PlaceSort.NAME) }
    var selectedIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val container = LocalContext.current.appContainer()
    val scopeLabel by produceState(initialValue = "", scope) { value = container.labels.label(scope) }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        container.onLocationPermissionChanged()
    }

    // Die Infos (Standort-Fortschritt, Auswahl-Hinweis, Berechtigung) ändern ihre Höhe ständig, solange noch Standorte
    // gelesen werden. Läge diese Zeile über der Karte, würde die Karte bei jeder Änderung springen. Darum liegt sie
    // bei der Karte als Überlagerung darüber (kein Einfluss auf die Kartengröße), in der Liste als erstes Element.
    val info: @Composable (Modifier) -> Unit = { modifier ->
        PlacesInfo(
            ui = ui,
            showScope = scope != MediaFilter.ALL,
            scopeLabel = scopeLabel,
            onClearScope = onClearScope,
            onRequestPermission = { locationLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) },
            modifier = modifier
        )
    }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.nav_places)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.places_tab_map)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.places_tab_list)) })
            }
            if (tab == 0) {
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    OsmPlacesMap(
                        places = ui.places,
                        selectedIds = selectedIds.toSet(),
                        onPlacesTapped = { selectedIds = it },
                        modifier = Modifier.fillMaxSize()
                    )
                    info(Modifier.align(Alignment.TopStart).padding(8.dp))
                    if (ui.loaded && ui.places.isEmpty()) {
                        Card(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                            Text(
                                stringResource(R.string.places_map_empty),
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                PlaceList(scope, ui, sort, { sort = it }, onOpenFilter, onShowCalendar, header = { info(Modifier.fillMaxWidth()) })
            }
        }
    }

    if (selectedIds.isNotEmpty()) {
        PlaceSheet(
            places = ui.places.filter { it.clusterId in selectedIds },
            scope = scope,
            onDismiss = { selectedIds = emptyList() },
            onOpen = { filter -> selectedIds = emptyList(); onOpenFilter(filter) },
            onSort = { filter -> selectedIds = emptyList(); onStartSorting(filter) },
            onCalendar = { filter -> selectedIds = emptyList(); onShowCalendar(filter) }
        )
    }
}

/** Standort-Fortschritt, Hinweis auf aktive Einschränkung und ggf. Berechtigungsbitte – kompakt und gut lesbar. */
@Composable
private fun PlacesInfo(
    ui: PlacesUi,
    showScope: Boolean,
    scopeLabel: String,
    onClearScope: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showStats = ui.locationAccess && ui.locations.total > 0
    if (ui.locationAccess && !showStats && !showScope) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!ui.locationAccess) {
            LocationBanner(onRequestPermission)
        } else if (showStats) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                tonalElevation = 2.dp
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.places_location_stats, ui.locations.withLocation, ui.locations.total, ui.locations.checked),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (ui.locations.checked < ui.locations.total) {
                        LinearProgressIndicator(
                            progress = { ui.locations.checked.toFloat() / ui.locations.total },
                            modifier = Modifier.padding(top = 6.dp).fillMaxWidth()
                        )
                    }
                }
            }
        }
        if (showScope) {
            ScopeChip(stringResource(R.string.scope_selection, scopeLabel), onClearScope)
        }
    }
}

@Composable
private fun LocationBanner(onRequest: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.places_no_location_access), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onRequest) { Text(stringResource(R.string.places_allow_location)) }
        }
    }
}

@Composable
private fun PlaceList(
    scope: MediaFilter,
    ui: PlacesUi,
    sort: PlaceSort,
    onSort: (PlaceSort) -> Unit,
    onOpenFilter: (MediaFilter) -> Unit,
    onShowCalendar: (MediaFilter) -> Unit,
    header: @Composable () -> Unit
) {
    val zone = ZoneId.systemDefault()
    val labels = LocalContext.current.appContainer().labels
    val sorted = when (sort) {
        PlaceSort.NAME -> ui.places.sortedBy { it.name.lowercase() }
        PlaceSort.COUNT -> ui.places.sortedByDescending { it.counts.total }
        PlaceSort.PROGRESS -> ui.places.sortedBy { it.counts.fraction }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(AppIcons.Sort, contentDescription = stringResource(R.string.places_sort))
            FilterChip(sort == PlaceSort.NAME, { onSort(PlaceSort.NAME) }, { Text(stringResource(R.string.places_sort_name)) })
            FilterChip(sort == PlaceSort.COUNT, { onSort(PlaceSort.COUNT) }, { Text(stringResource(R.string.places_sort_count)) })
            FilterChip(sort == PlaceSort.PROGRESS, { onSort(PlaceSort.PROGRESS) }, { Text(stringResource(R.string.places_sort_progress)) })
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "info") { header() }
            if (ui.noPlace.total > 0) {
                item(key = "no-place") {
                    PlaceRow(
                        title = stringResource(R.string.places_no_place),
                        subtitle = null,
                        counts = ui.noPlace,
                        onClick = { onOpenFilter(scope.copy(withoutPlace = true)) },
                        onCalendar = { onShowCalendar(MediaFilter(withoutPlace = true)) }
                    )
                }
            }
            items(sorted, key = { it.clusterId }) { place ->
                val range = if (place.firstAt > 0) {
                    val from = labels.dayShort(CaptureTime.localDate(place.firstAt, zone))
                    val to = labels.dayShort(CaptureTime.localDate(place.lastAt, zone))
                    if (from == to) from else "$from – $to"
                } else {
                    null
                }
                PlaceRow(
                    place.name, range, place.counts,
                    onClick = { onOpenFilter(scope.copy(clusterIds = setOf(place.clusterId))) },
                    onCalendar = { onShowCalendar(MediaFilter(clusterIds = setOf(place.clusterId))) }
                )
            }
            if (ui.places.isEmpty() && ui.noPlace.total == 0) {
                item { EmptyState(stringResource(R.string.places_empty_title), stringResource(R.string.places_empty_message)) }
            }
        }
    }
}

@Composable
private fun PlaceRow(title: String, subtitle: String?, counts: StatusCounts, onClick: () -> Unit, onCalendar: () -> Unit) {
    val description = title + ", " + stringResource(R.string.progress_x_of_y, counts.processed, counts.total)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick).semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StateMark(counts.state, counts.fraction, hasDeferred = counts.hasDeferred)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    pluralText(R.plurals.media_count, counts.total) + " · " + stringResource(R.string.progress_x_of_y, counts.processed, counts.total),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onCalendar) {
                Icon(AppIcons.Calendar, contentDescription = stringResource(R.string.action_show_in_calendar))
            }
            Icon(AppIcons.Chevron, contentDescription = null)
        }
    }
}

/**
 * Unterer Bereich nach Tipp auf einen Marker. Bei einem Cluster mit mehreren Orten steht oben die Summe aller
 * Medien dieser Orte (Sortieren, Ansehen, Kalender für alle zusammen); darunter lassen sich einzelne Orte wählen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceSheet(
    places: List<PlaceItem>,
    scope: MediaFilter,
    onDismiss: () -> Unit,
    onOpen: (MediaFilter) -> Unit,
    onSort: (MediaFilter) -> Unit,
    onCalendar: (MediaFilter) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val labels = LocalContext.current.appContainer().labels
    if (places.isEmpty()) return
    val total = places.fold(StatusCounts.EMPTY) { acc, p -> acc + p.counts }
    val allIds = places.map { it.clusterId }.toSet()
    val allFilter = scope.copy(clusterIds = allIds)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val title = if (places.size == 1) {
                places[0].name
            } else {
                pluralText(R.plurals.places_count, places.size)
            }
            PlaceActions(
                title = title,
                subtitle = if (places.size == 1) dateRange(places[0], labels, zone) else null,
                counts = total,
                onOpen = { onOpen(allFilter) },
                onSort = { onSort(allFilter) },
                onCalendar = { onCalendar(MediaFilter(clusterIds = allIds)) }
            )
            if (places.size > MAX_LISTED_PLACES) {
                Text(
                    stringResource(R.string.places_too_many, MAX_LISTED_PLACES),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (places.size > 1) {
                HorizontalDivider()
                Text(stringResource(R.string.places_single_places), style = MaterialTheme.typography.titleSmall)
                places.sortedBy { it.name.lowercase() }.forEach { place ->
                    val one = scope.copy(clusterIds = setOf(place.clusterId))
                    PlaceActions(
                        title = place.name,
                        subtitle = dateRange(place, labels, zone),
                        counts = place.counts,
                        onOpen = { onOpen(one) },
                        onSort = { onSort(one) },
                        onCalendar = { onCalendar(MediaFilter(clusterIds = setOf(place.clusterId))) },
                        compact = true
                    )
                }
            }
        }
    }
}

private fun dateRange(place: PlaceItem, labels: de.ingvoss.fotosortierer.data.media.FilterLabeler, zone: ZoneId): String? =
    if (place.firstAt > 0) {
        labels.dayShort(CaptureTime.localDate(place.firstAt, zone)) + " – " + labels.dayShort(CaptureTime.localDate(place.lastAt, zone))
    } else {
        null
    }

@Composable
private fun PlaceActions(
    title: String,
    subtitle: String?,
    counts: StatusCounts,
    onOpen: () -> Unit,
    onSort: () -> Unit,
    onCalendar: () -> Unit,
    compact: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        ProgressSummary(counts)
        // Zwei Tasten nebeneinander (je halbe Breite), "Im Kalender zeigen" darunter über die volle Breite –
        // drei Tasten in einer Zeile pressen die letzte so schmal, dass ihr Text buchstabenweise umbricht.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (counts.open > 0) {
                Button(onClick = onSort, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_start_sorting), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.places_show_media), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        OutlinedButton(onClick = onCalendar, modifier = Modifier.fillMaxWidth()) {
            Icon(AppIcons.Calendar, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.action_show_in_calendar), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
        }
    }
}

/** Mehr Einzelorte werden im Sheet nicht aufgelistet (Übersichtlichkeit); die Gesamtaktionen gelten weiterhin. */
private const val MAX_LISTED_PLACES = 40
