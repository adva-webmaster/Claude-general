package de.ingvoss.fotosortierer.ui.trash

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import de.ingvoss.fotosortierer.AppContainer
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.core.MediaStatus
import de.ingvoss.fotosortierer.core.StatusCounts
import de.ingvoss.fotosortierer.data.db.MediaStateEntity
import de.ingvoss.fotosortierer.data.media.DeleteBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Fortschritt des Löschens in Systemblöcken. */
data class DeleteProgress(
    val blocks: List<DeleteBlock>,
    val index: Int = 0,
    val deleted: Int = 0,
    val finished: Boolean = false,
    /** Der Nutzer hat eine Systemabfrage abgelehnt: der Rest bleibt im App-Papierkorb. */
    val cancelled: Boolean = false
) {
    val totalBlocks: Int get() = blocks.size
}

class TrashViewModel(private val container: AppContainer) : ViewModel() {
    private val media = container.media
    private val trash = container.trash
    private val filter = MediaFilter(statuses = setOf(MediaStatus.PAPIERKORB))

    val counts: StateFlow<StatusCounts> =
        media.counts(filter).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatusCounts.EMPTY)

    val bytes: StateFlow<Long> = container.database.mediaDao().observeTrashBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val items: Flow<PagingData<MediaStateEntity>> = media.pagedMedia(filter, oldestFirst = false).cachedIn(viewModelScope)

    val hintShown: StateFlow<Boolean> = container.settings.settings
        .map { it.trashHintShown }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection

    private val _progress = MutableStateFlow<DeleteProgress?>(null)
    val progress: StateFlow<DeleteProgress?> = _progress

    /** Von der Oberfläche abzuholende Systemabfrage (Bestätigungsdialog von Android). */
    private val _pendingRequest = MutableStateFlow<IntentSender?>(null)
    val pendingRequest: StateFlow<IntentSender?> = _pendingRequest

    fun toggle(uid: Long) = _selection.update { if (uid in it) it - uid else it + uid }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun selectAll() {
        viewModelScope.launch { _selection.value = media.uids(filter).toSet() }
    }

    fun markHintShown() {
        container.appScope.launch { container.settings.setTrashHintShown() }
    }

    /** Gibt die Auswahl (oder, ohne Auswahl, alle) in die Warteschlange zurück. */
    fun restore() {
        val selected = _selection.value
        container.appScope.launch {
            val uids = if (selected.isEmpty()) media.uids(filter) else selected.toList()
            trash.restore(uids)
            _selection.value = emptySet()
        }
    }

    /** Startet das endgültige Löschen der Auswahl (oder aller) über die Systemabfragen. */
    fun deleteForever() {
        val selected = _selection.value
        viewModelScope.launch {
            val blocks = if (selected.isEmpty()) trash.allBlocks() else trash.blocksFor(selected.toList())
            if (blocks.isEmpty()) return@launch
            _progress.value = DeleteProgress(blocks)
            requestCurrentBlock()
        }
    }

    private suspend fun requestCurrentBlock() {
        val state = _progress.value ?: return
        if (state.index >= state.totalBlocks) {
            _progress.value = state.copy(finished = true)
            _selection.value = emptySet()
            return
        }
        val sender = trash.createRequest(state.blocks[state.index])
        if (sender == null) {
            // Alle Medien des Blocks sind schon weg: überspringen.
            _progress.value = state.copy(index = state.index + 1)
            requestCurrentBlock()
        } else {
            _pendingRequest.value = sender
        }
    }

    /** Die Oberfläche hat die Systemabfrage gestartet. */
    fun requestLaunched() {
        _pendingRequest.value = null
    }

    /** Ergebnis der Systemabfrage: Nur nach Bestätigung (RESULT_OK) wird der Block als gelöscht markiert. */
    fun onDeleteResult(confirmed: Boolean) {
        val state = _progress.value ?: return
        // Im App-Scope: Der bestätigte Block muss auch dann als gelöscht gespeichert werden, wenn der Bildschirm
        // gerade verlassen wird.
        container.appScope.launch {
            if (confirmed) {
                val block = state.blocks[state.index]
                trash.markDeleted(block)
                _progress.value = state.copy(index = state.index + 1, deleted = state.deleted + block.uids.size)
                requestCurrentBlock()
            } else {
                _progress.value = state.copy(cancelled = true, finished = true)
            }
        }
    }

    fun dismissProgress() {
        _progress.value = null
    }
}
