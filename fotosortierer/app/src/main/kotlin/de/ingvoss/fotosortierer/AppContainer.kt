package de.ingvoss.fotosortierer

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import de.ingvoss.fotosortierer.data.backup.BackupManager
import de.ingvoss.fotosortierer.data.db.AppDatabase
import de.ingvoss.fotosortierer.data.media.AccessLevel
import de.ingvoss.fotosortierer.data.media.FilterLabeler
import de.ingvoss.fotosortierer.data.media.FolderRepository
import de.ingvoss.fotosortierer.data.media.GeocodingService
import de.ingvoss.fotosortierer.data.media.LocationIndexer
import de.ingvoss.fotosortierer.data.media.LocationReader
import de.ingvoss.fotosortierer.data.media.MediaAccess
import de.ingvoss.fotosortierer.data.media.MediaRepository
import de.ingvoss.fotosortierer.data.media.MediaStoreScanner
import de.ingvoss.fotosortierer.data.media.MediaSyncer
import de.ingvoss.fotosortierer.data.media.MissingMediaCleaner
import de.ingvoss.fotosortierer.data.media.PlaceBuilder
import de.ingvoss.fotosortierer.data.media.PlaceNamer
import de.ingvoss.fotosortierer.data.media.TrashRepository
import de.ingvoss.fotosortierer.data.settings.SessionStore
import de.ingvoss.fotosortierer.data.settings.SettingsRepository
import de.ingvoss.fotosortierer.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Zentrale Objektverwaltung (manuelle DI). Wird von [FotoApp] erzeugt. */
class AppContainer(val app: Application) {
    /** Lebt so lange wie der Prozess; für Arbeiten, die nicht an einen Bildschirm gebunden sind. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Nominatim verlangt einen identifizierenden User-Agent; auch osmdroid benutzt ihn für Kacheln. */
    val userAgent: String = "PixelSweeper/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"

    val database: AppDatabase = AppDatabase.create(app)
    val settings = SettingsRepository(app)
    val sessionStore = SessionStore(app)
    val scheduler = WorkScheduler(app)

    private val scanner = MediaStoreScanner(app)
    val placeBuilder = PlaceBuilder(database)
    val syncer = MediaSyncer(app, database, settings, scanner) { scheduler.enqueueLocationIndexing() }
    val locationIndexer = LocationIndexer(
        app, database, settings, LocationReader(app), placeBuilder
    ) { scheduler.enqueueGeocoding() }
    val placeNamer = PlaceNamer(database, GeocodingService(app, userAgent))

    val media = MediaRepository(database)
    val mediaCleaner = MissingMediaCleaner(app, database)
    val folders = FolderRepository(database, settings)
    val trash = TrashRepository(database, app.contentResolver)
    val backup = BackupManager(database, settings, app.contentResolver)
    val labels = FilterLabeler(app, database)

    private val _access = MutableStateFlow(MediaAccess.level(app))
    val access: StateFlow<AccessLevel> = _access
    private val _locationAccess = MutableStateFlow(MediaAccess.hasLocationAccess(app))
    val locationAccess: StateFlow<Boolean> = _locationAccess

    private val mediaChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Registriert den MediaStore-Beobachter (neue/gelöschte Medien lösen nach kurzer Ruhezeit einen Abgleich aus). */
    fun start() {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                mediaChanges.tryEmit(Unit)
            }
        }
        app.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        app.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        appScope.launch {
            mediaChanges.collectLatest {
                delay(CHANGE_DEBOUNCE_MS)
                syncer.sync()
            }
        }
    }

    /** Beim (Wieder-)Öffnen der App: Berechtigungen neu lesen und abgleichen. */
    fun onActivityStart() {
        refreshAccess()
        appScope.launch {
            // Beim Start zusätzlich prüfen, ob Dateien auf anderem Weg gelöscht wurden (Karteileichen im MediaStore).
            if (syncer.sync(verifyFiles = true)) {
                scheduler.enqueueLocationIndexing()
                scheduler.enqueueGeocoding()
                // Zusätzlich sofort im Vordergrund lesen: WorkManager startet Hintergrundarbeit gern verzögert.
                locationIndexer.run()
            }
        }
    }

    /** Nach (de)aktivierter Standort-Berechtigung: Standorte der Medien nachholen. */
    fun onLocationPermissionChanged() {
        refreshAccess()
        scheduler.enqueueLocationIndexing()
        appScope.launch { locationIndexer.run() }
    }

    fun refreshAccess() {
        _access.value = MediaAccess.level(app)
        _locationAccess.value = MediaAccess.hasLocationAccess(app)
    }

    // ---- Wartungsaktionen aus den Einstellungen ---------------------------------------------

    /** Ortsradius geändert: Orte neu bilden (Namen kommen größtenteils aus dem Cache). */
    fun onClusterRadiusChanged(meters: Int) {
        appScope.launch {
            placeBuilder.rebuild(meters.toDouble())
            scheduler.enqueueGeocoding()
        }
    }

    /** Medientypen/Alben geändert: Sichtbarkeit neu berechnen. */
    fun onVisibilitySettingsChanged() {
        appScope.launch { syncer.applyHiddenSettings() }
    }

    /**
     * "Datenbank neu aufbauen": Standorte, Orte und Ortsnamen werden verworfen und neu gelesen, alle Medien
     * neu abgeglichen. Die Entscheidungen (behalten/Papierkorb/zurückgestellt) bleiben erhalten.
     */
    fun rebuildDatabase() {
        appScope.launch {
            placeBuilder.resetAll()
            syncer.applyHiddenSettings()
            syncer.sync()
            scheduler.enqueueLocationIndexing()
        }
    }

    private companion object {
        const val CHANGE_DEBOUNCE_MS = 2000L
    }
}
