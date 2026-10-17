package de.ingvoss.fotosortierer.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.room.withTransaction
import de.ingvoss.fotosortierer.core.BackupDeleted
import de.ingvoss.fotosortierer.core.BackupItem
import de.ingvoss.fotosortierer.core.BackupLocation
import de.ingvoss.fotosortierer.core.BackupPlanner
import de.ingvoss.fotosortierer.core.BackupSection
import de.ingvoss.fotosortierer.core.KnownMedia
import de.ingvoss.fotosortierer.core.MediaKind
import de.ingvoss.fotosortierer.core.MediaStatus
import de.ingvoss.fotosortierer.core.SwipeMapping
import de.ingvoss.fotosortierer.data.db.AppDatabase
import de.ingvoss.fotosortierer.data.db.GeocodeCacheEntity
import de.ingvoss.fotosortierer.data.db.KnownRow
import de.ingvoss.fotosortierer.data.db.MediaStateEntity
import de.ingvoss.fotosortierer.data.settings.AppSettings
import de.ingvoss.fotosortierer.data.settings.SettingsRepository
import de.ingvoss.fotosortierer.data.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Was ein Export enthalten könnte (Anzahlen für die Auswahlseite). */
data class BackupSummary(val decisions: Int, val deleted: Int, val locations: Int, val placeNames: Int)

/** Inhalt einer gelesenen Sicherungsdatei; [sections] sind die Abschnitte, die die Datei tatsächlich enthält. */
class BackupData(
    val sections: Set<BackupSection>,
    val settings: AppSettings?,
    val decisions: List<BackupItem>,
    val deleted: List<BackupDeleted>,
    val locations: List<BackupLocation>,
    val placeNames: List<GeocodeCacheEntity>
)

/** Ergebnis eines Imports (nur für gewählte Abschnitte aussagekräftig). */
data class ImportResult(
    val decisionsApplied: Int = 0,
    val decisionsUnmatched: Int = 0,
    val deletedAdded: Int = 0,
    val locationsApplied: Int = 0,
    val placeNamesRestored: Int = 0,
    val settingsApplied: Boolean = false,
    val foldersApplied: Boolean = false
)

/** Die Datei ist keine (lesbare) Sicherung dieser App. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * JSON-Export/-Import über den Storage Access Framework (vom Nutzer gewählte Datei, keine zusätzliche Berechtigung).
 * Abschnitte ([BackupSection]) sind einzeln wählbar: Entscheidungen, gelöschte Medien, Standorte samt Ortsnamen,
 * Einstellungen, Ordner. Medien werden beim Import über Name, Aufnahmezeit und Größe zugeordnet, weil sich
 * MediaStore-IDs ändern können. Dateien älterer Versionen (Format 1) bleiben lesbar.
 */
class BackupManager(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val resolver: ContentResolver
) {
    suspend fun summary(): BackupSummary = withContext(Dispatchers.IO) {
        val dao = db.mediaDao()
        BackupSummary(dao.countDecisions(), dao.countDeleted(), dao.countWithLocation(), db.placeDao().cacheCount())
    }

    /** Schreibt die gewählten Abschnitte; Rückgabe: Zahl der gesicherten Medien (Entscheidungen + Gelöschte). */
    suspend fun export(target: Uri, sections: Set<BackupSection>): Int = withContext(Dispatchers.IO) {
        doExport(target, sections)
    }

    /** Liest die Datei vollständig, ohne etwas zu verändern (für die Auswahl vor dem Import). */
    suspend fun read(source: Uri): BackupData = withContext(Dispatchers.IO) { doRead(source) }

    /** Übernimmt die gewählten Abschnitte aus [data]. */
    suspend fun apply(data: BackupData, sections: Set<BackupSection>): ImportResult = withContext(Dispatchers.IO) {
        doApply(data, sections.intersect(data.sections))
    }

    // ---- Export -------------------------------------------------------------------------------

    private suspend fun doExport(target: Uri, sections: Set<BackupSection>): Int {
        val dao = db.mediaDao()
        val decisions = if (BackupSection.DECISIONS in sections) {
            dao.nonDefault().filter { it.mediaStatus != MediaStatus.GELOESCHT }
        } else {
            emptyList()
        }
        val deleted = if (BackupSection.DELETED in sections) dao.deletedMedia() else emptyList()
        val located = if (BackupSection.LOCATIONS in sections) dao.mediaWithLocation() else emptyList()
        val names = if (BackupSection.LOCATIONS in sections) db.placeDao().allCache() else emptyList()
        val current = settings.current()
        val stream = resolver.openOutputStream(target, "wt") ?: throw IOException("Ziel nicht beschreibbar")
        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            JsonWriter(writer).use { json ->
                json.setIndent("")
                json.beginObject()
                json.name("app").value(APP_ID)
                json.name("version").value(FORMAT_VERSION.toLong())
                json.name("exportedAt").value(System.currentTimeMillis())
                json.name("sections").beginArray()
                sections.forEach { json.value(it.key) }
                json.endArray()
                val general = BackupSection.SETTINGS in sections
                val folders = BackupSection.FOLDERS in sections
                if (general || folders) {
                    json.name("settings")
                    writeSettings(json, current, general, folders)
                }
                if (BackupSection.DECISIONS in sections) {
                    json.name("items").beginArray()
                    for (m in decisions) {
                        json.beginObject()
                        json.name("v").value(m.volume)
                        json.name("id").value(m.mediaStoreId)
                        json.name("n").value(m.displayName)
                        json.name("t").value(m.takenAtUtc)
                        json.name("s").value(m.sizeBytes)
                        json.name("st").value(m.status.toLong())
                        json.name("c").value(m.statusChangedAt)
                        json.endObject()
                    }
                    json.endArray()
                }
                if (BackupSection.DELETED in sections) {
                    json.name("deleted").beginArray()
                    for (m in deleted) writeDeleted(json, m)
                    json.endArray()
                }
                if (BackupSection.LOCATIONS in sections) {
                    json.name("locations").beginArray()
                    for (m in located) {
                        val lat = m.latitude ?: continue
                        val lon = m.longitude ?: continue
                        json.beginObject()
                        json.name("v").value(m.volume)
                        json.name("id").value(m.mediaStoreId)
                        json.name("n").value(m.displayName)
                        json.name("t").value(m.takenAtUtc)
                        json.name("s").value(m.sizeBytes)
                        json.name("lat").value(lat)
                        json.name("lon").value(lon)
                        json.endObject()
                    }
                    json.endArray()
                    json.name("placeNames").beginArray()
                    for (e in names) {
                        json.beginObject()
                        json.name("k").value(e.cellKey)
                        json.name("n").value(e.name)
                        json.name("r").value(e.resolvedAt)
                        json.endObject()
                    }
                    json.endArray()
                }
                json.endObject()
            }
        }
        return decisions.size + deleted.size
    }

    private fun writeDeleted(json: JsonWriter, m: MediaStateEntity) {
        json.beginObject()
        json.name("v").value(m.volume)
        json.name("id").value(m.mediaStoreId)
        json.name("n").value(m.displayName)
        json.name("a").value(m.album)
        json.name("p").value(m.relativePath)
        json.name("k").value(m.kind.toLong())
        json.name("t").value(m.takenAtUtc)
        json.name("d").value(m.localDate)
        json.name("m").value(m.localMonth)
        json.name("s").value(m.sizeBytes)
        json.name("du").value(m.durationMs)
        json.name("c").value(m.statusChangedAt)
        json.endObject()
    }

    // ---- Import -------------------------------------------------------------------------------

    private fun doRead(source: Uri): BackupData {
        val input = resolver.openInputStream(source) ?: throw IOException("Datei nicht lesbar")
        val decisions = ArrayList<BackupItem>()
        val deleted = ArrayList<BackupDeleted>()
        val locations = ArrayList<BackupLocation>()
        val names = ArrayList<GeocodeCacheEntity>()
        var parsed: ParsedSettings? = null
        var hasItems = false
        var hasDeleted = false
        var hasLocations = false
        try {
            JsonReader(input.bufferedReader(Charsets.UTF_8)).use { json ->
                var appId: String? = null
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "app" -> appId = json.nextString()
                        "settings" -> parsed = readSettings(json)
                        "items" -> { hasItems = true; readItems(json, decisions) }
                        "deleted" -> { hasDeleted = true; readDeleted(json, deleted) }
                        "locations" -> { hasLocations = true; readLocations(json, locations) }
                        "placeNames" -> readPlaceNames(json, names)
                        else -> json.skipValue()
                    }
                }
                json.endObject()
                if (appId != APP_ID && appId != LEGACY_APP_ID) throw InvalidBackupException("Keine PixelSweeper-Sicherung")
            }
        } catch (e: IllegalStateException) {
            throw InvalidBackupException("Ungültiges Dateiformat", e)
        } catch (e: java.io.EOFException) {
            throw InvalidBackupException("Unvollständige Datei", e)
        } catch (e: NumberFormatException) {
            throw InvalidBackupException("Ungültige Zahl in der Datei", e)
        } catch (e: java.net.ProtocolException) {
            throw InvalidBackupException("Ungültiges Dateiformat", e)
        }
        val sections = buildSet {
            if (hasItems) add(BackupSection.DECISIONS)
            if (hasDeleted) add(BackupSection.DELETED)
            if (hasLocations) add(BackupSection.LOCATIONS)
            parsed?.let {
                if (it.hasGeneral) add(BackupSection.SETTINGS)
                if (it.hasFolders) add(BackupSection.FOLDERS)
            }
        }
        return BackupData(sections, parsed?.settings, decisions, deleted, locations, names)
    }

    private suspend fun doApply(data: BackupData, sections: Set<BackupSection>): ImportResult {
        val dao = db.mediaDao()
        val known = dao.allKnown().map { it.toKnown() }
        // Entscheidungen und Positionen gehören zu vorhandenen Medien; Tombstones kommen dafür nicht in Frage.
        val live = known.filter { it.status != MediaStatus.GELOESCHT }
        var result = ImportResult()

        if (BackupSection.DECISIONS in sections) {
            val plan = BackupPlanner.plan(data.decisions, live)
            db.withTransaction {
                for (m in plan.matches) dao.setStatus(m.uid, m.status.code, m.statusChangedAt)
            }
            result = result.copy(decisionsApplied = plan.matches.size, decisionsUnmatched = plan.unmatched)
        }

        if (BackupSection.DELETED in sections) {
            val plan = BackupPlanner.planDeleted(data.deleted, known)
            if (plan.toInsert.isNotEmpty()) {
                // Die Dateien gibt es nicht mehr; die Zeilen sollen nur Statistik und Kalender wiederherstellen.
                // Künstliches Volume mit negativen IDs, damit nichts mit echten Medien kollidiert und der Abgleich
                // diese Einträge nie anfasst.
                var nextId = dao.minMediaStoreId(RESTORED_VOLUME).coerceAtMost(0L)
                val entities = plan.toInsert.map { d ->
                    nextId -= 1
                    MediaStateEntity(
                        mediaStoreId = nextId,
                        volume = RESTORED_VOLUME,
                        takenAtUtc = d.takenAtUtc,
                        localDate = d.localDate,
                        localMonth = d.localMonth,
                        album = d.album,
                        kind = d.kind.code,
                        sizeBytes = d.sizeBytes,
                        status = MediaStatus.GELOESCHT.code,
                        statusChangedAt = d.statusChangedAt,
                        displayName = d.displayName,
                        durationMs = d.durationMs,
                        locationChecked = true,
                        hidden = false,
                        relativePath = d.relativePath
                    )
                }
                db.withTransaction {
                    for (chunk in entities.chunked(INSERT_CHUNK)) dao.insertAll(chunk)
                }
            }
            result = result.copy(deletedAdded = plan.toInsert.size)
        }

        if (BackupSection.LOCATIONS in sections) {
            val plan = BackupPlanner.planLocations(data.locations, live)
            db.withTransaction {
                for (m in plan.matches) dao.setLocation(m.uid, m.latitude, m.longitude)
                if (data.placeNames.isNotEmpty()) db.placeDao().putCaches(data.placeNames)
            }
            result = result.copy(locationsApplied = plan.matches.size, placeNamesRestored = data.placeNames.size)
        }

        val general = BackupSection.SETTINGS in sections
        val folders = BackupSection.FOLDERS in sections
        val imported = data.settings
        if (imported != null && (general || folders)) {
            settings.applyImported(imported, general, folders)
            result = result.copy(settingsApplied = general, foldersApplied = folders)
        }
        return result
    }

    private fun KnownRow.toKnown() = KnownMedia(
        uid, volume, mediaStoreId, MediaStatus.fromCode(status), MediaKind.fromCode(kind),
        displayName, album, sizeBytes, durationMs, takenAtUtc, localDate, localMonth, relativePath
    )

    // ---- Einstellungen ------------------------------------------------------------------------

    private class ParsedSettings(val settings: AppSettings, val hasGeneral: Boolean, val hasFolders: Boolean)

    private fun writeSettings(json: JsonWriter, s: AppSettings, general: Boolean, folders: Boolean) {
        json.beginObject()
        if (general) {
            json.name("swipeLinks").value(s.swipeMapping.links.name)
            json.name("swipeRechts").value(s.swipeMapping.rechts.name)
            json.name("swipeOben").value(s.swipeMapping.oben.name)
            json.name("oldestFirst").value(s.oldestFirst)
            json.name("radius").value(s.clusterRadiusMeters.toLong())
            json.name("photos").value(s.showPhotos)
            json.name("videos").value(s.showVideos)
            json.name("screenshots").value(s.showScreenshots)
            json.name("theme").value(s.themeMode.name)
            json.name("haptics").value(s.haptics)
            json.name("videoSound").value(s.videoSoundDefault)
            json.name("infoLine").value(s.showInfoLine)
        }
        if (folders) {
            json.name("excludedAlbums").beginArray()
            s.excludedAlbums.forEach { json.value(it) }
            json.endArray()
            json.name("favoriteAlbums").beginArray()
            s.favoriteAlbums.forEach { json.value(it) }
            json.endArray()
        }
        json.endObject()
    }

    private fun readSettings(json: JsonReader): ParsedSettings {
        var left: String? = null
        var right: String? = null
        var up: String? = null
        var s = AppSettings()
        var general = false
        var folders = false
        json.beginObject()
        while (json.hasNext()) {
            when (json.nextName()) {
                "swipeLinks" -> { left = json.nextString(); general = true }
                "swipeRechts" -> { right = json.nextString(); general = true }
                "swipeOben" -> { up = json.nextString(); general = true }
                "oldestFirst" -> { s = s.copy(oldestFirst = json.nextBoolean()); general = true }
                "radius" -> { s = s.copy(clusterRadiusMeters = json.nextInt()); general = true }
                "photos" -> { s = s.copy(showPhotos = json.nextBoolean()); general = true }
                "videos" -> { s = s.copy(showVideos = json.nextBoolean()); general = true }
                "screenshots" -> { s = s.copy(showScreenshots = json.nextBoolean()); general = true }
                "excludedAlbums" -> { s = s.copy(excludedAlbums = readStringSet(json)); folders = true }
                "favoriteAlbums" -> { s = s.copy(favoriteAlbums = readStringSet(json)); folders = true }
                "theme" -> {
                    val name = json.nextString()
                    s = s.copy(themeMode = ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.SYSTEM)
                    general = true
                }
                "haptics" -> { s = s.copy(haptics = json.nextBoolean()); general = true }
                "videoSound" -> { s = s.copy(videoSoundDefault = json.nextBoolean()); general = true }
                "infoLine" -> { s = s.copy(showInfoLine = json.nextBoolean()); general = true }
                else -> json.skipValue()
            }
        }
        json.endObject()
        return ParsedSettings(s.copy(swipeMapping = SwipeMapping.fromNames(left, right, up)), general, folders)
    }

    private fun readStringSet(json: JsonReader): Set<String> {
        val out = LinkedHashSet<String>()
        json.beginArray()
        while (json.hasNext()) out.add(json.nextString())
        json.endArray()
        return out
    }

    // ---- Abschnitte lesen ---------------------------------------------------------------------

    private fun readItems(json: JsonReader, out: MutableList<BackupItem>) {
        json.beginArray()
        while (json.hasNext()) {
            var volume = ""
            var id = 0L
            var name = ""
            var taken = 0L
            var size = 0L
            var status = -1
            var changed = 0L
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "v" -> volume = json.nextString()
                    "id" -> id = json.nextLong()
                    "n" -> name = json.nextString()
                    "t" -> taken = json.nextLong()
                    "s" -> size = json.nextLong()
                    "st" -> status = json.nextInt()
                    "c" -> changed = json.nextLong()
                    else -> skip(json)
                }
            }
            json.endObject()
            val parsed = MediaStatus.entries.firstOrNull { it.code == status }
            if (parsed != null && parsed != MediaStatus.UNBEARBEITET && parsed != MediaStatus.GELOESCHT) {
                out.add(BackupItem(volume, id, name, taken, size, parsed, changed))
            }
        }
        json.endArray()
    }

    private fun readDeleted(json: JsonReader, out: MutableList<BackupDeleted>) {
        json.beginArray()
        while (json.hasNext()) {
            var volume = ""
            var id = 0L
            var name = ""
            var album = ""
            var path = ""
            var kind = MediaKind.FOTO.code
            var taken = 0L
            var date = ""
            var month = ""
            var size = 0L
            var duration = 0L
            var changed = 0L
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "v" -> volume = json.nextString()
                    "id" -> id = json.nextLong()
                    "n" -> name = json.nextString()
                    "a" -> album = json.nextString()
                    "p" -> path = json.nextString()
                    "k" -> kind = json.nextInt()
                    "t" -> taken = json.nextLong()
                    "d" -> date = json.nextString()
                    "m" -> month = json.nextString()
                    "s" -> size = json.nextLong()
                    "du" -> duration = json.nextLong()
                    "c" -> changed = json.nextLong()
                    else -> skip(json)
                }
            }
            json.endObject()
            // Ohne Datum lässt sich ein Eintrag nicht im Kalender einordnen.
            if (date.length >= MONTH_LENGTH) {
                out.add(
                    BackupDeleted(
                        volume, id, name, album, path, MediaKind.fromCode(kind), taken, date,
                        month.ifEmpty { date.take(MONTH_LENGTH) }, size, duration, changed
                    )
                )
            }
        }
        json.endArray()
    }

    private fun readLocations(json: JsonReader, out: MutableList<BackupLocation>) {
        json.beginArray()
        while (json.hasNext()) {
            var volume = ""
            var id = 0L
            var name = ""
            var taken = 0L
            var size = 0L
            var lat = Double.NaN
            var lon = Double.NaN
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "v" -> volume = json.nextString()
                    "id" -> id = json.nextLong()
                    "n" -> name = json.nextString()
                    "t" -> taken = json.nextLong()
                    "s" -> size = json.nextLong()
                    "lat" -> lat = json.nextDouble()
                    "lon" -> lon = json.nextDouble()
                    else -> skip(json)
                }
            }
            json.endObject()
            if (!lat.isNaN() && !lon.isNaN()) out.add(BackupLocation(volume, id, name, taken, size, lat, lon))
        }
        json.endArray()
    }

    private fun readPlaceNames(json: JsonReader, out: MutableList<GeocodeCacheEntity>) {
        json.beginArray()
        while (json.hasNext()) {
            var key: String? = null
            var name: String? = null
            var resolved = 0L
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "k" -> key = json.nextString()
                    "n" -> if (json.peek() == JsonToken.NULL) json.nextNull() else name = json.nextString()
                    "r" -> resolved = json.nextLong()
                    else -> skip(json)
                }
            }
            json.endObject()
            key?.let { out.add(GeocodeCacheEntity(it, name, resolved)) }
        }
        json.endArray()
    }

    private fun skip(json: JsonReader) {
        if (json.peek() == JsonToken.NULL) json.nextNull() else json.skipValue()
    }

    companion object {
        const val APP_ID = "PixelSweeper"

        /** So hieß die App vor der Umbenennung; ältere Sicherungen bleiben lesbar. */
        const val LEGACY_APP_ID = "Fotosortierer"

        /** Künstliches Volume für aus der Sicherung wiederhergestellte gelöschte Medien (negative IDs). */
        const val RESTORED_VOLUME = "restored"

        private const val FORMAT_VERSION = 2
        private const val INSERT_CHUNK = 500
        private const val MONTH_LENGTH = 7 // "2026-03"
    }
}
