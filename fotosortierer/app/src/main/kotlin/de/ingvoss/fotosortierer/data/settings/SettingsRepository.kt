package de.ingvoss.fotosortierer.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.ingvoss.fotosortierer.core.SwipeAction
import de.ingvoss.fotosortierer.core.SwipeDirection
import de.ingvoss.fotosortierer.core.SwipeMapping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persistente Einstellungen (DataStore Preferences). */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val LEFT = stringPreferencesKey("swipe_links")
        val RIGHT = stringPreferencesKey("swipe_rechts")
        val UP = stringPreferencesKey("swipe_oben")
        val OLDEST_FIRST = booleanPreferencesKey("oldest_first")
        val RADIUS = intPreferencesKey("cluster_radius_m")
        val PHOTOS = booleanPreferencesKey("show_photos")
        val VIDEOS = booleanPreferencesKey("show_videos")
        val SCREENSHOTS = booleanPreferencesKey("show_screenshots")
        val EXCLUDED_ALBUMS = stringSetPreferencesKey("excluded_albums")
        val THEME = stringPreferencesKey("theme_mode")
        val HAPTICS = booleanPreferencesKey("haptics")
        val VIDEO_SOUND = booleanPreferencesKey("video_sound_default")
        val INFO_LINE = booleanPreferencesKey("info_line")
        val TRASH_HINT = booleanPreferencesKey("trash_hint_shown")
        val PERMISSION_REQUESTED = booleanPreferencesKey("permission_requested")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            swipeMapping = SwipeMapping.fromNames(this[Keys.LEFT], this[Keys.RIGHT], this[Keys.UP]),
            oldestFirst = this[Keys.OLDEST_FIRST] ?: defaults.oldestFirst,
            clusterRadiusMeters = this[Keys.RADIUS] ?: defaults.clusterRadiusMeters,
            showPhotos = this[Keys.PHOTOS] ?: defaults.showPhotos,
            showVideos = this[Keys.VIDEOS] ?: defaults.showVideos,
            showScreenshots = this[Keys.SCREENSHOTS] ?: defaults.showScreenshots,
            excludedAlbums = this[Keys.EXCLUDED_ALBUMS] ?: defaults.excludedAlbums,
            themeMode = this[Keys.THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: defaults.themeMode,
            haptics = this[Keys.HAPTICS] ?: defaults.haptics,
            videoSoundDefault = this[Keys.VIDEO_SOUND] ?: defaults.videoSoundDefault,
            showInfoLine = this[Keys.INFO_LINE] ?: defaults.showInfoLine,
            trashHintShown = this[Keys.TRASH_HINT] ?: defaults.trashHintShown
        )
    }

    /** Merkt, ob der Berechtigungsdialog schon einmal gezeigt wurde (Unterscheidung "dauerhaft abgelehnt"). */
    val permissionRequested: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PERMISSION_REQUESTED] ?: false }

    suspend fun setPermissionRequested() {
        context.settingsStore.edit { it[Keys.PERMISSION_REQUESTED] = true }
    }

    suspend fun setSwipeMapping(mapping: SwipeMapping) {
        context.settingsStore.edit {
            it[Keys.LEFT] = mapping.links.name
            it[Keys.RIGHT] = mapping.rechts.name
            it[Keys.UP] = mapping.oben.name
        }
    }

    /** Weist einer Richtung eine Aktion zu; die bisherige Aktion der Richtung wandert zur Gegenstelle. */
    suspend fun assignSwipe(direction: SwipeDirection, action: SwipeAction) {
        val updated = current().swipeMapping.with(direction, action)
        setSwipeMapping(updated)
    }

    suspend fun setOldestFirst(value: Boolean) = edit { it[Keys.OLDEST_FIRST] = value }
    suspend fun setClusterRadius(meters: Int) = edit { it[Keys.RADIUS] = meters }
    suspend fun setShowPhotos(value: Boolean) = edit { it[Keys.PHOTOS] = value }
    suspend fun setShowVideos(value: Boolean) = edit { it[Keys.VIDEOS] = value }
    suspend fun setShowScreenshots(value: Boolean) = edit { it[Keys.SCREENSHOTS] = value }
    suspend fun setExcludedAlbums(albums: Set<String>) = edit { it[Keys.EXCLUDED_ALBUMS] = albums }
    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }
    suspend fun setHaptics(value: Boolean) = edit { it[Keys.HAPTICS] = value }
    suspend fun setVideoSoundDefault(value: Boolean) = edit { it[Keys.VIDEO_SOUND] = value }
    suspend fun setShowInfoLine(value: Boolean) = edit { it[Keys.INFO_LINE] = value }
    suspend fun setTrashHintShown() = edit { it[Keys.TRASH_HINT] = true }

    /** Überschreibt alle Einstellungen (Import). */
    suspend fun replaceAll(value: AppSettings) {
        context.settingsStore.edit {
            it[Keys.LEFT] = value.swipeMapping.links.name
            it[Keys.RIGHT] = value.swipeMapping.rechts.name
            it[Keys.UP] = value.swipeMapping.oben.name
            it[Keys.OLDEST_FIRST] = value.oldestFirst
            it[Keys.RADIUS] = value.clusterRadiusMeters
            it[Keys.PHOTOS] = value.showPhotos
            it[Keys.VIDEOS] = value.showVideos
            it[Keys.SCREENSHOTS] = value.showScreenshots
            it[Keys.EXCLUDED_ALBUMS] = value.excludedAlbums
            it[Keys.THEME] = value.themeMode.name
            it[Keys.HAPTICS] = value.haptics
            it[Keys.VIDEO_SOUND] = value.videoSoundDefault
            it[Keys.INFO_LINE] = value.showInfoLine
            it[Keys.TRASH_HINT] = value.trashHintShown
        }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsStore.edit { block(it) }
    }
}
