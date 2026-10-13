package de.ingvoss.fotosortierer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.ingvoss.fotosortierer.AppContainer
import de.ingvoss.fotosortierer.core.FolderEntry
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.core.QueuePhase
import de.ingvoss.fotosortierer.core.StatusCounts
import de.ingvoss.fotosortierer.data.db.LocationProgressRow
import de.ingvoss.fotosortierer.data.media.AccessLevel
import de.ingvoss.fotosortierer.data.media.SyncState
import de.ingvoss.fotosortierer.data.settings.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUi(
    val overall: StatusCounts = StatusCounts.EMPTY,
    val trashBytes: Long = 0L,
    val freedBytes: Long = 0L,
    val session: SessionState? = null,
    /** Zähler des Filters, mit dem "Weitermachen" einsteigt. */
    val continueCounts: StatusCounts = StatusCounts.EMPTY,
    val sync: SyncState = SyncState(),
    val locations: LocationProgressRow = LocationProgressRow(0, 0, 0),
    val access: AccessLevel = AccessLevel.NONE,
    val locationAccess: Boolean = false,
    val namesPending: Int = 0,
    val loaded: Boolean = false
) {
    val continueFilter: MediaFilter get() = session?.filter ?: MediaFilter.ALL
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val dao = container.database.mediaDao()

    /** Favorisierte Ordner mit Zählern für die Schnellwahl auf der Startseite. */
    val favorites: StateFlow<List<FolderEntry>> = container.folders.overview
        .map { it.favorites }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val state: StateFlow<HomeUi> = combine(
        combine(dao.observeOverall(), dao.observeTrashBytes(), dao.observeFreedBytes()) { overall, trash, freed ->
            Triple(overall.toCounts(), trash, freed)
        },
        combine(container.sessionStore.state, container.syncer.state, dao.observeLocationProgress()) { s, sync, loc ->
            Triple(s, sync, loc)
        },
        combine(container.access, container.locationAccess, container.database.placeDao().observePendingCount()) { a, l, n ->
            Triple(a, l, n)
        },
        container.sessionStore.state.flatMapLatest { s ->
            if (s?.phase == QueuePhase.ERNEUT) {
                // Erneutes Sortieren: "offen" = noch nicht erneut entschiedene Medien der Sitzung.
                container.media.queueCountFlow(s.filter, s.phase, s.phase2StartedAt).map { StatusCounts(unbearbeitet = it) }
            } else {
                container.media.counts(s?.filter ?: MediaFilter.ALL)
            }
        }
    ) { totals, session, access, continueCounts ->
        HomeUi(
            overall = totals.first,
            trashBytes = totals.second,
            freedBytes = totals.third,
            session = session.first,
            continueCounts = continueCounts,
            sync = session.second,
            locations = session.third,
            access = access.first,
            locationAccess = access.second,
            namesPending = access.third,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUi())
}
