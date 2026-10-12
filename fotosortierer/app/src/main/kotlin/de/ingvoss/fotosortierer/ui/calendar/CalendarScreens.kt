package de.ingvoss.fotosortierer.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.ingvoss.fotosortierer.AppContainer
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.FilterCodec
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.core.MonthLayout
import de.ingvoss.fotosortierer.core.ProgressState
import de.ingvoss.fotosortierer.core.StatusCounts
import de.ingvoss.fotosortierer.data.db.GroupCountsRow
import de.ingvoss.fotosortierer.ui.appContainer
import de.ingvoss.fotosortierer.ui.components.AppTopBar
import de.ingvoss.fotosortierer.ui.components.EmptyState
import de.ingvoss.fotosortierer.ui.components.ProgressSummary
import de.ingvoss.fotosortierer.ui.components.ScopeChip
import de.ingvoss.fotosortierer.ui.components.StateMark
import de.ingvoss.fotosortierer.ui.components.pluralText
import de.ingvoss.fotosortierer.ui.containerViewModel
import de.ingvoss.fotosortierer.ui.theme.AppIcons
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Kalenderdaten, optional eingeschränkt auf Orte ([scope], z. B. "nur dieser Ort"): Monate und Tage zählen dann
 * nur die Medien dieser Orte.
 */
class CalendarViewModel(container: AppContainer, private val scope: MediaFilter) : ViewModel() {
    private val media = container.media

    /** Alle Monate mit Zählern; null solange nicht geladen. */
    val months: StateFlow<List<GroupCountsRow>?> =
        media.months(scope).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun days(month: String): Flow<List<GroupCountsRow>> = media.days(scope.copy(localMonth = month))
}

private fun yearOf(monthKey: String): Int = monthKey.substringBefore('-').toIntOrNull() ?: 1970

// ---- Ebene 1: Jahre ----------------------------------------------------------------------------

@Composable
fun CalendarYearsScreen(
    scope: MediaFilter,
    onOpenYear: (Int) -> Unit,
    onShowMap: (MediaFilter) -> Unit,
    onClearScope: () -> Unit
) {
    val viewModel = containerViewModel(key = "cal-" + FilterCodec.encode(scope)) { CalendarViewModel(it, scope) }
    val months by viewModel.months.collectAsStateWithLifecycle()
    val labels = LocalContext.current.appContainer().labels
    val scopeLabel by produceState(initialValue = "", scope) { value = labels.label(scope) }

    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.nav_calendar), actions = {
                MapAction { onShowMap(scope) }
            })
        }
    ) { padding ->
        val data = months
        when {
            data == null -> Box(Modifier.fillMaxSize().padding(padding))
            data.isEmpty() -> EmptyState(
                stringResource(R.string.calendar_empty_title),
                stringResource(R.string.calendar_empty_message),
                Modifier.padding(padding)
            )
            else -> {
                val years = data.groupBy { yearOf(it.groupKey) }
                    .mapValues { (_, rows) -> rows.map { it.toCounts() }.fold(StatusCounts.EMPTY) { a, b -> a + b } }
                    .toList()
                    .sortedByDescending { it.first }
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (scope.hasPlaceScope) {
                        ScopeChip(
                            stringResource(R.string.scope_place, scopeLabel),
                            onClearScope,
                            Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(years, key = { it.first }) { (year, counts) ->
                            PeriodCard(
                                title = labels.year(year),
                                counts = counts,
                                onClick = { onOpenYear(year) },
                                onShowMap = { onShowMap(scope.copy(localYear = year.toString())) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodCard(title: String, counts: StatusCounts, onClick: () -> Unit, onShowMap: () -> Unit) {
    val description = title + ", " + stringResource(R.string.progress_x_of_y, counts.processed, counts.total)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick).semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                ProgressSummary(counts)
            }
            MapAction(onShowMap)
            Icon(AppIcons.Chevron, contentDescription = null)
        }
    }
}

/** "Sortieren" (falls etwas offen ist) und "Medien ansehen" nebeneinander, je halbe Breite. */
@Composable
private fun ActionRow(
    canSort: Boolean,
    sortLabel: String,
    onSort: () -> Unit,
    onShowMedia: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canSort) {
            Button(onClick = onSort, modifier = Modifier.weight(1f)) {
                Text(sortLabel, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        OutlinedButton(onClick = onShowMedia, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.action_show_media), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

/** Schaltfläche "Auf Karte zeigen". */
@Composable
private fun MapAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(AppIcons.Pin, contentDescription = stringResource(R.string.action_show_on_map))
    }
}

// ---- Ebene 2: Monate eines Jahres --------------------------------------------------------------

@Composable
fun CalendarMonthsScreen(
    scope: MediaFilter,
    year: Int,
    onBack: () -> Unit,
    onOpenMonth: (String) -> Unit,
    onSortYear: (MediaFilter) -> Unit,
    onShowMedia: (MediaFilter) -> Unit,
    onShowMap: (MediaFilter) -> Unit
) {
    val viewModel = containerViewModel(key = "cal-" + FilterCodec.encode(scope)) { CalendarViewModel(it, scope) }
    val months by viewModel.months.collectAsStateWithLifecycle()
    val labels = LocalContext.current.appContainer().labels
    val byKey = months.orEmpty().associateBy { it.groupKey }
    val yearFilter = scope.copy(localYear = year.toString())
    val total = byKey.filterKeys { it.startsWith("$year-") }.values.map { it.toCounts() }.fold(StatusCounts.EMPTY) { a, b -> a + b }

    Scaffold(
        topBar = {
            AppTopBar(labels.year(year), onBack = onBack, actions = { MapAction { onShowMap(yearFilter) } })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (total.total > 0) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgressSummary(total)
                    ActionRow(
                        canSort = total.open > 0,
                        sortLabel = stringResource(R.string.calendar_sort_year),
                        onSort = { onSortYear(yearFilter) },
                        onShowMedia = { onShowMedia(yearFilter) }
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(MonthLayout.months(year)) { month ->
                    val key = month.toString()
                    val counts = byKey[key]?.toCounts() ?: StatusCounts.EMPTY
                    MonthTile(month, counts, onClick = { onOpenMonth(key) })
                }
            }
        }
    }
}

@Composable
private fun MonthTile(month: YearMonth, counts: StatusCounts, onClick: () -> Unit) {
    val name = month.month.getDisplayName(TextStyle.FULL, Locale.GERMANY)
    val enabled = counts.total > 0
    val summary = if (enabled) stringResource(R.string.progress_x_of_y, counts.processed, counts.total) else stringResource(R.string.calendar_no_media)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .semantics(mergeDescendants = true) { contentDescription = "$name, $summary" },
        colors = CardDefaults.cardColors(
            containerColor = if (counts.state == ProgressState.VOLLSTAENDIG) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StateMark(counts.state, counts.fraction, hasDeferred = counts.hasDeferred)
            Column {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---- Ebene 3: Tage eines Monats ----------------------------------------------------------------

@Composable
fun CalendarDaysScreen(
    scope: MediaFilter,
    month: String,
    onBack: () -> Unit,
    onOpenDay: (MediaFilter) -> Unit,
    onSortMonth: (MediaFilter) -> Unit,
    onShowMedia: (MediaFilter) -> Unit,
    onShowMap: (MediaFilter) -> Unit
) {
    val viewModel = containerViewModel(key = "cal-" + FilterCodec.encode(scope)) { CalendarViewModel(it, scope) }
    val daysFlow = remember(month) { viewModel.days(month) }
    val days by daysFlow.collectAsStateWithLifecycle<List<GroupCountsRow>?>(initialValue = null)
    val labels = LocalContext.current.appContainer().labels
    val byDate = days.orEmpty().associateBy { it.groupKey }
    val total = days.orEmpty().map { it.toCounts() }.fold(StatusCounts.EMPTY) { a, b -> a + b }
    val yearMonth = remember(month) { runCatching { YearMonth.parse(month) }.getOrNull() }

    Scaffold(
        topBar = {
            AppTopBar(labels.month(month), onBack = onBack, actions = { MapAction { onShowMap(scope.copy(localMonth = month)) } })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            if (total.total > 0) {
                ProgressSummary(total, Modifier.padding(vertical = 8.dp))
                ActionRow(
                    canSort = total.open > 0,
                    sortLabel = stringResource(R.string.calendar_sort_month),
                    onSort = { onSortMonth(scope.copy(localMonth = month)) },
                    onShowMedia = { onShowMedia(scope.copy(localMonth = month)) },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (yearMonth != null) {
                WeekdayHeader()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(MonthLayout.cells(yearMonth)) { date ->
                        if (date == null) {
                            Box(Modifier.aspectRatio(0.8f))
                        } else {
                            DayCell(date, byDate[date.toString()]?.toCounts() ?: StatusCounts.EMPTY) { onOpenDay(scope.copy(localDate = date.toString())) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val names = listOf(
            R.string.weekday_mo, R.string.weekday_di, R.string.weekday_mi, R.string.weekday_do,
            R.string.weekday_fr, R.string.weekday_sa, R.string.weekday_so
        )
        names.forEach {
            Text(
                stringResource(it),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, counts: StatusCounts, onOpen: () -> Unit) {
    val enabled = counts.total > 0
    val labels = LocalContext.current.appContainer().labels
    val dayName = labels.dayShort(date.toString())
    val description = if (enabled) {
        dayName + ", " + stringResource(R.string.progress_x_of_y, counts.processed, counts.total)
    } else {
        dayName + ", " + stringResource(R.string.calendar_no_media)
    }
    Column(
        Modifier
            .aspectRatio(0.8f)
            .let { if (enabled) it.clickable(onClick = onOpen) else it }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        if (enabled) {
            StateMark(counts.state, counts.fraction, markSize = 24.dp, hasDeferred = counts.hasDeferred)
            Text(counts.total.toString(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

@Composable
fun CountsLine(counts: StatusCounts) {
    Text(pluralText(R.plurals.media_count, counts.total), style = MaterialTheme.typography.bodySmall)
}
