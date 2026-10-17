package de.ingvoss.fotosortierer.ui.settings

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.ingvoss.fotosortierer.AppContainer
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.SwipeAction
import de.ingvoss.fotosortierer.core.SwipeDirection
import de.ingvoss.fotosortierer.core.BackupSection
import de.ingvoss.fotosortierer.data.backup.BackupData
import de.ingvoss.fotosortierer.data.backup.BackupSummary
import de.ingvoss.fotosortierer.data.backup.ImportResult
import de.ingvoss.fotosortierer.data.backup.InvalidBackupException
import de.ingvoss.fotosortierer.data.db.GroupCountsRow
import de.ingvoss.fotosortierer.data.settings.AppSettings
import de.ingvoss.fotosortierer.data.settings.ThemeMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/** Einmalige Rückmeldung (Schnellleiste) mit Textressource und Formatargumenten. */
class SettingsMessage(@StringRes val resId: Int, val args: List<Any> = emptyList())

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.settings

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Alle Alben mit Zählern, auch bereits ausgeblendete. */
    val albums: StateFlow<List<GroupCountsRow>> = container.database.mediaDao().observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages: Flow<SettingsMessage> = _messages.receiveAsFlow()

    fun assignSwipe(direction: SwipeDirection, action: SwipeAction) {
        container.appScope.launch { repo.assignSwipe(direction, action) }
    }

    fun setOldestFirst(value: Boolean) {
        container.appScope.launch { repo.setOldestFirst(value) }
    }

    fun setClusterRadius(meters: Int) {
        container.appScope.launch {
            repo.setClusterRadius(meters)
            container.onClusterRadiusChanged(meters)
        }
    }

    fun setShowPhotos(value: Boolean) = visibility { repo.setShowPhotos(value) }
    fun setShowVideos(value: Boolean) = visibility { repo.setShowVideos(value) }
    fun setShowScreenshots(value: Boolean) = visibility { repo.setShowScreenshots(value) }
    fun setExcludedAlbums(albums: Set<String>) = visibility { repo.setExcludedAlbums(albums) }

    private fun visibility(change: suspend () -> Unit) {
        container.appScope.launch {
            change()
            container.onVisibilitySettingsChanged()
        }
    }

    fun setTheme(mode: ThemeMode) {
        container.appScope.launch { repo.setThemeMode(mode) }
    }

    fun setHaptics(value: Boolean) {
        container.appScope.launch { repo.setHaptics(value) }
    }

    fun setVideoSound(value: Boolean) {
        container.appScope.launch { repo.setVideoSoundDefault(value) }
    }

    fun setInfoLine(value: Boolean) {
        container.appScope.launch { repo.setShowInfoLine(value) }
    }

    // ---- Sicherung: Auswahlseiten für Export und Import ---------------------------------------

    private var pendingExport: Set<BackupSection> = emptySet()
    private val _exportSummary = MutableStateFlow<BackupSummary?>(null)

    /** Gesetzt, solange die Auswahlseite für den Export offen ist (mit den Anzahlen je Abschnitt). */
    val exportSummary: StateFlow<BackupSummary?> = _exportSummary.asStateFlow()

    private val _importData = MutableStateFlow<BackupData?>(null)

    /** Gesetzt, solange die Auswahlseite für den Import offen ist (gelesene Datei). */
    val importData: StateFlow<BackupData?> = _importData.asStateFlow()

    fun prepareExport() {
        viewModelScope.launch { _exportSummary.value = container.backup.summary() }
    }

    fun cancelExport() {
        _exportSummary.value = null
    }

    /** Merkt die Auswahl; danach fragt die Oberfläche nach dem Zielort und ruft [export] auf. */
    fun chooseExport(sections: Set<BackupSection>) {
        pendingExport = sections
        _exportSummary.value = null
    }

    fun export(target: Uri) {
        val sections = pendingExport
        container.appScope.launch {
            val message = try {
                SettingsMessage(R.string.settings_export_done, listOf(container.backup.export(target, sections)))
            } catch (e: IOException) {
                SettingsMessage(R.string.settings_io_error)
            }
            _messages.send(message)
        }
    }

    /** Liest die Datei und öffnet die Auswahlseite; verändert noch nichts. */
    fun readImport(source: Uri) {
        viewModelScope.launch {
            try {
                _importData.value = container.backup.read(source)
            } catch (e: InvalidBackupException) {
                _messages.send(SettingsMessage(R.string.settings_import_invalid))
            } catch (e: IOException) {
                _messages.send(SettingsMessage(R.string.settings_io_error))
            }
        }
    }

    fun cancelImport() {
        _importData.value = null
    }

    fun applyImport(sections: Set<BackupSection>) {
        val data = _importData.value ?: return
        _importData.value = null
        container.appScope.launch {
            val message = try {
                val result = container.backup.apply(data, sections)
                container.onVisibilitySettingsChanged()
                if (BackupSection.LOCATIONS in sections || BackupSection.SETTINGS in sections) {
                    container.onClusterRadiusChanged(repo.current().clusterRadiusMeters)
                }
                importMessage(result)
            } catch (e: IOException) {
                SettingsMessage(R.string.settings_io_error)
            }
            _messages.send(message)
        }
    }

    private fun importMessage(result: ImportResult): SettingsMessage {
        val app = container.app
        val parts = buildList {
            if (result.decisionsApplied > 0 || result.decisionsUnmatched > 0) {
                add(app.getString(R.string.backup_part_decisions, result.decisionsApplied, result.decisionsUnmatched))
            }
            if (result.deletedAdded > 0) add(app.getString(R.string.backup_part_deleted, result.deletedAdded))
            if (result.locationsApplied > 0) add(app.getString(R.string.backup_part_locations, result.locationsApplied))
            if (result.settingsApplied) add(app.getString(R.string.backup_part_settings))
            if (result.foldersApplied) add(app.getString(R.string.backup_part_folders))
        }
        return if (parts.isEmpty()) {
            SettingsMessage(R.string.settings_import_nothing)
        } else {
            SettingsMessage(R.string.settings_import_done, listOf(parts.joinToString(", ")))
        }
    }

    fun rebuildDatabase() {
        container.rebuildDatabase()
        container.appScope.launch { _messages.send(SettingsMessage(R.string.settings_rebuild_started)) }
    }
}
