package de.ingvoss.fotosortierer.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import de.ingvoss.fotosortierer.AppContainer
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.core.MediaStatus
import de.ingvoss.fotosortierer.core.StatusCounts
import de.ingvoss.fotosortierer.data.db.MediaStateEntity
import de.ingvoss.fotosortierer.data.media.StatusSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Meldung nach einer Statusänderung (für die "Rückgängig"-Schnellleiste). */
data class ChangeEvent(val count: Int, val status: MediaStatus)

/**
 * Raster der Medien eines Filters (Tag, Monat, Ort, Album …) mit Korrekturfunktion: Status ändern per
 * Antippen oder Mehrfachauswahl (langer Druck). Das Raster ist ein Paging-3-Strom und deshalb auch bei
 * 50 000+ Medien flüssig.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FilterDetailViewModel(private val container: AppContainer, val filter: MediaFilter) : ViewModel() {
    private val media = container.media

    /** Zähler des ganzen Filters, unabhängig von einer Statusauswahl. */
    val counts: StateFlow<StatusCounts> =
        media.counts(filter.copy(statuses = null)).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatusCounts.EMPTY)

    // Kommt der Filter mit genau einem Status (z. B. Tipp auf "Behalten" in der Statistik), ist er vorgewählt.
    private val _statusFilter = MutableStateFlow<MediaStatus?>(filter.statuses?.singleOrNull())
    val statusFilter: StateFlow<MediaStatus?> = _statusFilter

    private val activeFilter: Flow<MediaFilter> =
        _statusFilter.map { s -> filter.copy(statuses = s?.let { setOf(it) }) }

    val items: Flow<PagingData<MediaStateEntity>> = combine(
        activeFilter,
        container.settings.settings.map { it.oldestFirst }.distinctUntilChanged()
    ) { f, oldestFirst -> f to oldestFirst }
        .flatMapLatest { (f, oldestFirst) -> media.pagedMedia(f, oldestFirst) }
        .cachedIn(viewModelScope)

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection

    private val changes = Channel<ChangeEvent>(Channel.BUFFERED)
    val changeEvents: Flow<ChangeEvent> = changes.receiveAsFlow()
    private var lastChange: List<StatusSnapshot> = emptyList()

    fun setStatusFilter(status: MediaStatus?) {
        _statusFilter.value = status
        _selection.value = emptySet()
    }

    fun toggleSelection(uid: Long) {
        _selection.update { if (uid in it) it - uid else it + uid }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /** Wählt alle Medien des aktuellen Filters (nicht nur die geladenen Seiten). */
    fun selectAll() {
        viewModelScope.launch {
            val f = filter.copy(statuses = _statusFilter.value?.let { setOf(it) })
            _selection.value = media.uids(f).toSet()
        }
    }

    fun applyToSelection(status: MediaStatus) {
        val uids = _selection.value.toList()
        if (uids.isEmpty()) return
        _selection.value = emptySet()
        change(uids, status)
    }

    fun applyTo(uid: Long, status: MediaStatus) = change(listOf(uid), status)

    private fun change(uids: List<Long>, status: MediaStatus) {
        container.appScope.launch {
            lastChange = media.setStatusBulk(uids, status)
            changes.send(ChangeEvent(uids.size, status))
        }
    }

    fun undoLastChange() {
        val snapshots = lastChange
        if (snapshots.isEmpty()) return
        lastChange = emptyList()
        container.appScope.launch { media.restore(snapshots) }
    }
}
