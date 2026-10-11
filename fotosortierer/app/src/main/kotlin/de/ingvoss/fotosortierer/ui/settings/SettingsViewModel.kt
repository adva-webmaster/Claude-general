package de.ingvoss.fotosortierer.ui.settings

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.ingvoss.fotosortierer.AppContainer
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.SwipeAction
import de.ingvoss.fotosortierer.core.SwipeDirection
import de.ingvoss.fotosortierer.data.backup.InvalidBackupException
import de.ingvoss.fotosortierer.data.db.GroupCountsRow
import de.ingvoss.fotosortierer.data.settings.AppSettings
import de.ingvoss.fotosortierer.data.settings.ThemeMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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

    fun export(target: Uri) {
        container.appScope.launch {
            val message = try {
                SettingsMessage(R.string.settings_export_done, listOf(container.backup.export(target)))
            } catch (e: IOException) {
                SettingsMessage(R.string.settings_io_error)
            }
            _messages.send(message)
        }
    }

    fun import(source: Uri) {
        container.appScope.launch {
            val message = try {
                val result = container.backup.import(source)
                container.onVisibilitySettingsChanged()
                container.onClusterRadiusChanged(repo.current().clusterRadiusMeters)
                SettingsMessage(R.string.settings_import_done, listOf(result.applied, result.unmatched))
            } catch (e: InvalidBackupException) {
                SettingsMessage(R.string.settings_import_invalid)
            } catch (e: IOException) {
                SettingsMessage(R.string.settings_io_error)
            }
            _messages.send(message)
        }
    }

    fun rebuildDatabase() {
        container.rebuildDatabase()
        container.appScope.launch { _messages.send(SettingsMessage(R.string.settings_rebuild_started)) }
    }
}
