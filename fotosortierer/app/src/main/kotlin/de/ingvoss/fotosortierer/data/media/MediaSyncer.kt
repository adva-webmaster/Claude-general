package de.ingvoss.fotosortierer.data.media

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import de.ingvoss.fotosortierer.core.KnownMedia
import de.ingvoss.fotosortierer.core.MediaKey
import de.ingvoss.fotosortierer.core.MediaKind
import de.ingvoss.fotosortierer.core.MissingFileGuard
import de.ingvoss.fotosortierer.core.MediaStatus
import de.ingvoss.fotosortierer.core.ScannedMedia
import de.ingvoss.fotosortierer.core.SyncPlanner
import de.ingvoss.fotosortierer.data.db.AppDatabase
import de.ingvoss.fotosortierer.data.db.KnownRow
import de.ingvoss.fotosortierer.data.db.MediaStateEntity
import de.ingvoss.fotosortierer.data.settings.AppSettings
import de.ingvoss.fotosortierer.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Gleicht den MediaStore mit der Datenbank ab:
 *  - neue Medien → UNBEARBEITET (ein neues Medium in einem erledigten Tag macht den Tag automatisch
 *    wieder "teilweise", weil Tagesstatus aus den Medien abgeleitet wird),
 *  - auf anderem Weg gelöschte Medien (nicht mehr im MediaStore, oder Datei fehlt trotz Eintrag) → Zeile wird
 *    aus der Datenbank entfernt, außer ihr Volume ist gerade nicht eingehängt oder der Zugriff ist nur teilweise.
 *    Nur von der App selbst gelöschte Medien bleiben als Tombstone (GELOESCHT) in der Statistik,
 *  - geänderte Metadaten → aktualisieren, wiederaufgetauchte Tombstones → UNBEARBEITET.
 * Es läuft immer höchstens ein Abgleich gleichzeitig.
 */
class MediaSyncer(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val scanner: MediaStoreScanner,
    private val onNewMedia: () -> Unit
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    @Volatile
    private var lastVerifiedAt = 0L

    /**
     * @param verifyFiles zusätzlich prüfen, ob die Dateien der gelisteten Medien noch existieren (nur bei vollem
     *   Zugriff und höchstens alle [VERIFY_INTERVAL_MS]).
     * @return true, wenn ein Abgleich stattgefunden hat (false bei fehlendem Zugriff oder Fehler).
     */
    suspend fun sync(verifyFiles: Boolean = false): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) { doSync(verifyFiles) }
    }

    private suspend fun doSync(verifyFiles: Boolean): Boolean {
        val access = MediaAccess.level(context)
        if (access == AccessLevel.NONE) return false
        _state.update { it.copy(running = true, scanned = 0, lastError = null) }
        return try {
            val zone = ZoneId.systemDefault()
            val volumes = scanner.volumes()
            val scanned = ArrayList<ScannedMedia>()
            for (volume in volumes) {
                val base = scanned.size
                scanned += scanner.scan(volume, zone) { n -> _state.update { it.copy(scanned = base + n) } }
            }
            val dao = db.mediaDao()
            val known = dao.allKnown().map { it.toKnown() }
            // Bei Teilzugriff fehlen nicht ausgewählte Medien im Scan – sie dürfen nicht als gelöscht gelten.
            val goneVolumes = if (access == AccessLevel.FULL) volumes else emptySet()
            val current = settings.current()
            val now = System.currentTimeMillis()
            val missingFiles = HashSet<MediaKey>()
            if (verifyFiles && access == AccessLevel.FULL && now - lastVerifiedAt > VERIFY_INTERVAL_MS) {
                for (volume in volumes) {
                    val check = scanner.checkFiles(volume)
                    if (MissingFileGuard.trusted(check.checked, check.missing.size)) {
                        missingFiles += check.missing
                    } else {
                        Log.w(TAG, "Dateiprüfung ${volume}: ${check.missing.size} von ${check.checked} fehlen – ignoriert")
                    }
                }
                lastVerifiedAt = now
            }
            val plan = SyncPlanner.plan(known, scanned, goneVolumes, missingFiles)
            db.withTransaction {
                for (chunk in plan.inserts.chunked(INSERT_CHUNK)) {
                    dao.insertAll(chunk.map { it.toEntity(current) })
                }
                for (u in plan.updates) {
                    val s = u.scanned
                    dao.updateMeta(
                        u.uid, s.time.utcMillis, s.time.localDate, s.time.localMonth, s.album, s.kind.code,
                        s.sizeBytes, s.durationMs, s.displayName, isHidden(current, s.kind, s.album)
                    )
                }
                for (r in plan.revivals) {
                    val s = r.scanned
                    dao.updateMeta(
                        r.uid, s.time.utcMillis, s.time.localDate, s.time.localMonth, s.album, s.kind.code,
                        s.sizeBytes, s.durationMs, s.displayName, isHidden(current, s.kind, s.album)
                    )
                    dao.setStatus(r.uid, MediaStatus.UNBEARBEITET.code, now)
                }
                for (chunk in plan.removals.map { it.uid }.chunked(UID_CHUNK)) {
                    dao.deleteByUids(chunk)
                }
                if (plan.removals.isNotEmpty()) db.placeDao().deleteEmpty()
            }
            _state.update {
                it.copy(running = false, lastSyncAt = now, lastInserted = plan.inserts.size + plan.revivals.size)
            }
            if (plan.inserts.isNotEmpty() || plan.revivals.isNotEmpty()) onNewMedia()
            true
        } catch (e: SecurityException) {
            fail(e)
        } catch (e: IllegalStateException) {
            fail(e)
        } catch (e: android.database.sqlite.SQLiteException) {
            fail(e)
        }
    }

    private fun fail(e: Exception): Boolean {
        Log.w(TAG, "Abgleich fehlgeschlagen", e)
        _state.update { it.copy(running = false, lastError = e.message ?: e.javaClass.simpleName) }
        return false
    }

    /** Wendet geänderte Einstellungen "Medientypen" und "ausgeschlossene Alben" auf alle Medien an. */
    suspend fun applyHiddenSettings() {
        val s = settings.current()
        db.mediaDao().applyHidden(s.hiddenKinds.map { it.code }, s.excludedAlbums.toList())
    }

    private fun ScannedMedia.toEntity(s: AppSettings) = MediaStateEntity(
        mediaStoreId = mediaStoreId,
        volume = volume,
        takenAtUtc = time.utcMillis,
        localDate = time.localDate,
        localMonth = time.localMonth,
        album = album,
        kind = kind.code,
        sizeBytes = sizeBytes,
        status = MediaStatus.UNBEARBEITET.code,
        statusChangedAt = 0L,
        displayName = displayName,
        durationMs = durationMs,
        locationChecked = false,
        hidden = isHidden(s, kind, album)
    )

    private fun isHidden(s: AppSettings, kind: MediaKind, album: String): Boolean =
        kind in s.hiddenKinds || album in s.excludedAlbums

    private fun KnownRow.toKnown() = KnownMedia(
        uid = uid,
        volume = volume,
        mediaStoreId = mediaStoreId,
        status = MediaStatus.fromCode(status),
        kind = MediaKind.fromCode(kind),
        displayName = displayName,
        album = album,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        takenAtUtc = takenAtUtc,
        localDate = localDate,
        localMonth = localMonth
    )

    private companion object {
        const val TAG = "MediaSyncer"
        const val INSERT_CHUNK = 500
        const val UID_CHUNK = 500
        const val VERIFY_INTERVAL_MS = 30 * 60 * 1000L
    }
}
