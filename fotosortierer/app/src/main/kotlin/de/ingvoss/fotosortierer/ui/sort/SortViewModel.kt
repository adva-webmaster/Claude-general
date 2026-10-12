package de.ingvoss.fotosortierer.ui.sort

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.ingvoss.fotosortierer.AppContainer
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.core.QueuePhase
import de.ingvoss.fotosortierer.core.StatusCounts
import de.ingvoss.fotosortierer.core.SwipeAction
import de.ingvoss.fotosortierer.core.UndoStack
import de.ingvoss.fotosortierer.data.db.MediaStateEntity
import de.ingvoss.fotosortierer.data.db.SessionSummaryRow
import de.ingvoss.fotosortierer.data.media.StatusSnapshot
import de.ingvoss.fotosortierer.data.settings.AppSettings
import de.ingvoss.fotosortierer.data.settings.SessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

enum class SortStage {
    /** Karten werden sortiert. */
    SORTING,

    /** Phase "Unbearbeitet" ist durch; ggf. Zurückgestellte durchgehen. */
    PHASE_DONE,

    /** Nichts mehr offen (oder auch die zweite Phase beendet). */
    ALL_DONE
}

data class SortUiState(
    val loading: Boolean = true,
    val stage: SortStage = SortStage.SORTING,
    /** cards[0] ist die oberste Karte; dahinter die Vorlade-Karten. */
    val cards: List<MediaStateEntity> = emptyList(),
    val phase: QueuePhase = QueuePhase.UNBEARBEITET,
    val counts: StatusCounts = StatusCounts.EMPTY,
    val canUndo: Boolean = false,
    val deferredRemaining: Int = 0,
    val summary: SessionSummaryRow? = null,
    val trashTotal: Int = 0,
    /** Erneutes Sortieren bereits entschiedener Medien (Filter mit Statusangabe). */
    val review: Boolean = false,
    val reviewTotal: Int = 0,
    val reviewRemaining: Int = 0
) {
    /** "14 von 48": normal = bereits bearbeitete + aktuelle Karte; beim erneuten Sortieren = erledigte + aktuelle. */
    val headerPosition: Int
        get() = if (review) (reviewTotal - reviewRemaining + 1).coerceIn(1, reviewTotal.coerceAtLeast(1))
        else (counts.processed + 1).coerceAtMost(counts.total.coerceAtLeast(1))

    val headerTotal: Int get() = if (review) reviewTotal else counts.total
}

private data class UndoEntry(val snapshot: StatusSnapshot, val phase: QueuePhase)

/**
 * Sortiersitzung. Die Karten stammen aus einer Warteschlangen-Abfrage (aktuelles Medium + Vorlade-Karten);
 * jede Entscheidung wird sofort in Room geschrieben. Dadurch ergibt sich die Position nach einem Neustart
 * von selbst (nächstes unbearbeitetes Medium); Filter und Phase hält [de.ingvoss.fotosortierer.data.settings.SessionStore].
 * Die Oberfläche bekommt die nächste Karte sofort (optimistisch); Schreibzugriffe laufen der Reihe nach.
 */
class SortViewModel(
    private val container: AppContainer,
    val filter: MediaFilter,
    resume: Boolean
) : ViewModel() {
    private val media = container.media
    private val undoStack = UndoStack<UndoEntry>()
    /** Entscheidungen, die die Oberfläche schon zeigt, aber Room noch nicht geschrieben hat. */
    private val pending: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val mutex = Mutex()
    private var session: SessionState? = null
    private var summaryJob: Job? = null
    private var reviewTotal = 0

    private val _state = MutableStateFlow(SortUiState())
    val state: StateFlow<SortUiState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    init {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val stored = container.sessionStore.current()
            session = if (resume && stored != null && stored.filter == filter) {
                stored
            } else {
                container.sessionStore.start(filter, now)
            }
            load()
        }
        viewModelScope.launch {
            media.counts(filter.copy(statuses = null)).collect { counts -> _state.update { it.copy(counts = counts) } }
        }
        // Reihenfolge in den Einstellungen geändert → Warteschlange neu laden.
        viewModelScope.launch {
            settings.map { it.oldestFirst }.distinctUntilChanged().drop(1).collect { load() }
        }
    }

    // ---- Warteschlange ----------------------------------------------------------------------

    private suspend fun load() {
        mutex.withLock {
            val s = session ?: return
            val oldestFirst = container.settings.current().oldestFirst
            val items = media.queue(filter, s.phase, s.phase2StartedAt, oldestFirst, QUEUE_SIZE + pending.size)
                .filter { it.uid !in pending }
                .take(QUEUE_SIZE)
            val review = s.phase == QueuePhase.ERNEUT
            var remaining = 0
            if (review) {
                remaining = media.queueCount(filter, s.phase, s.phase2StartedAt)
                if (reviewTotal == 0) reviewTotal = remaining
            }
            if (items.isNotEmpty()) {
                summaryJob?.cancel()
                _state.update {
                    it.copy(
                        loading = false, stage = SortStage.SORTING, cards = items, phase = s.phase,
                        canUndo = !undoStack.isEmpty, review = review, reviewTotal = reviewTotal, reviewRemaining = remaining
                    )
                }
            } else {
                showSummary(s)
            }
        }
    }

    private suspend fun showSummary(s: SessionState) {
        // Beim erneuten Sortieren gibt es keine zweite Phase für Zurückgestellte.
        val deferred = if (s.phase == QueuePhase.ERNEUT) 0 else media.queueCount(filter, QueuePhase.ZURUECKGESTELLT, Long.MAX_VALUE)
        val trashTotal = media.trashCount()
        val stage = if (s.phase == QueuePhase.UNBEARBEITET && deferred > 0) SortStage.PHASE_DONE else SortStage.ALL_DONE
        _state.update {
            it.copy(
                loading = false,
                stage = stage,
                cards = emptyList(),
                phase = s.phase,
                deferredRemaining = deferred,
                trashTotal = trashTotal,
                canUndo = !undoStack.isEmpty
            )
        }
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            media.sessionSummary(filter, s.startedAt).collect { row -> _state.update { it.copy(summary = row) } }
        }
    }

    // ---- Entscheidungen ---------------------------------------------------------------------

    /**
     * Übernimmt die Entscheidung für die oberste Karte (nach der Ausflug-Animation). Geschrieben wird im
     * App-Scope, damit die Entscheidung auch dann gespeichert wird, wenn der Bildschirm sofort verlassen wird.
     */
    fun decide(action: SwipeAction) {
        val s = session ?: return
        val card = _state.value.cards.firstOrNull() ?: return
        pending += card.uid
        undoStack.push(UndoEntry(StatusSnapshot(card.uid, card.mediaStatus, card.statusChangedAt), s.phase))
        _state.update { it.copy(cards = it.cards.drop(1), canUndo = true) }
        val write = container.appScope.launch {
            mutex.withLock {
                media.setStatus(card.uid, action.status)
                pending -= card.uid
            }
        }
        viewModelScope.launch {
            write.join()
            load()
        }
    }

    fun undo() {
        val entry = undoStack.pop() ?: return
        _state.update { it.copy(canUndo = !undoStack.isEmpty) }
        val write = container.appScope.launch { mutex.withLock { media.restore(listOf(entry.snapshot)) } }
        viewModelScope.launch {
            write.join()
            session = session?.let { container.sessionStore.setPhase(it, entry.phase) }
            load()
        }
    }

    // ---- Abschluss --------------------------------------------------------------------------

    /** Zweite Phase: die in dieser Sitzung (und früher) zurückgestellten Medien nochmals durchgehen. */
    fun startDeferredPhase() {
        val s = session ?: return
        viewModelScope.launch {
            session = container.sessionStore.enterDeferredPhase(s, System.currentTimeMillis())
            _state.update { it.copy(loading = true) }
            load()
        }
    }

    /** Beendet die Sitzung ("Fertig"): "Weitermachen" startet danach eine neue. */
    fun finish() {
        container.appScope.launch { container.sessionStore.clear() }
    }

    private companion object {
        const val QUEUE_SIZE = 4
    }
}
