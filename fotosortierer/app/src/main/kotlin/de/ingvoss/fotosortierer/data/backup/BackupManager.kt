package de.ingvoss.fotosortierer.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import de.ingvoss.fotosortierer.core.BackupItem
import de.ingvoss.fotosortierer.core.BackupPlanner
import de.ingvoss.fotosortierer.core.KnownMedia
import de.ingvoss.fotosortierer.core.MediaKind
import de.ingvoss.fotosortierer.core.MediaStatus
import de.ingvoss.fotosortierer.core.SwipeMapping
import de.ingvoss.fotosortierer.data.db.AppDatabase
import de.ingvoss.fotosortierer.data.settings.AppSettings
import de.ingvoss.fotosortierer.data.settings.SettingsRepository
import de.ingvoss.fotosortierer.data.settings.ThemeMode
import java.io.IOException

/** Ergebnis eines Imports. */
data class ImportResult(val applied: Int, val unmatched: Int, val settingsApplied: Boolean)

/** Die Datei ist keine (lesbare) Sicherung dieser App. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * JSON-Export/-Import der Entscheidungen und Einstellungen über den Storage Access Framework (vom Nutzer
 * gewählte Datei, keine zusätzliche Berechtigung). Gesichert werden Medien mit Entscheidung
 * (behalten/Papierkorb/zurückgestellt); beim Import werden sie über Name, Aufnahmezeit und Größe
 * zugeordnet, weil sich MediaStore-IDs ändern können.
 */
class BackupManager(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val resolver: ContentResolver
) {
    suspend fun export(target: Uri): Int = withContext(Dispatchers.IO) { doExport(target) }

    suspend fun import(source: Uri): ImportResult = withContext(Dispatchers.IO) { doImport(source) }

    private suspend fun doExport(target: Uri): Int {
        val items = db.mediaDao().nonDefault().filter { it.mediaStatus != MediaStatus.GELOESCHT }
        val current = settings.current()
        val stream = resolver.openOutputStream(target, "wt") ?: throw IOException("Ziel nicht beschreibbar")
        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            JsonWriter(writer).use { json ->
                json.setIndent("")
                json.beginObject()
                json.name("app").value(APP_ID)
                json.name("version").value(FORMAT_VERSION.toLong())
                json.name("exportedAt").value(System.currentTimeMillis())
                json.name("settings")
                writeSettings(json, current)
                json.name("items").beginArray()
                for (m in items) {
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
                json.endObject()
            }
        }
        return items.size
    }

    private suspend fun doImport(source: Uri): ImportResult {
        val input = resolver.openInputStream(source) ?: throw IOException("Datei nicht lesbar")
        val items = ArrayList<BackupItem>()
        var importedSettings: AppSettings? = null
        try {
            JsonReader(input.bufferedReader(Charsets.UTF_8)).use { json ->
                var appId: String? = null
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "app" -> appId = json.nextString()
                        "settings" -> importedSettings = readSettings(json)
                        "items" -> readItems(json, items)
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

        val dao = db.mediaDao()
        val known = dao.allKnown().map {
            KnownMedia(
                it.uid, it.volume, it.mediaStoreId, MediaStatus.fromCode(it.status), MediaKind.fromCode(it.kind),
                it.displayName, it.album, it.sizeBytes, it.durationMs, it.takenAtUtc, it.localDate, it.localMonth
            )
        }
        val plan = BackupPlanner.plan(items, known)
        db.withTransaction {
            for (m in plan.matches) dao.setStatus(m.uid, m.status.code, m.statusChangedAt)
        }
        importedSettings?.let { settings.replaceAll(it) }
        return ImportResult(plan.matches.size, plan.unmatched, importedSettings != null)
    }

    // ---- Einstellungen ------------------------------------------------------------------------

    private fun writeSettings(json: JsonWriter, s: AppSettings) {
        json.beginObject()
        json.name("swipeLinks").value(s.swipeMapping.links.name)
        json.name("swipeRechts").value(s.swipeMapping.rechts.name)
        json.name("swipeOben").value(s.swipeMapping.oben.name)
        json.name("oldestFirst").value(s.oldestFirst)
        json.name("radius").value(s.clusterRadiusMeters.toLong())
        json.name("photos").value(s.showPhotos)
        json.name("videos").value(s.showVideos)
        json.name("screenshots").value(s.showScreenshots)
        json.name("excludedAlbums").beginArray()
        s.excludedAlbums.forEach { json.value(it) }
        json.endArray()
        json.name("favoriteAlbums").beginArray()
        s.favoriteAlbums.forEach { json.value(it) }
        json.endArray()
        json.name("theme").value(s.themeMode.name)
        json.name("haptics").value(s.haptics)
        json.name("videoSound").value(s.videoSoundDefault)
        json.name("infoLine").value(s.showInfoLine)
        json.endObject()
    }

    private fun readSettings(json: JsonReader): AppSettings {
        var left: String? = null
        var right: String? = null
        var up: String? = null
        var s = AppSettings()
        json.beginObject()
        while (json.hasNext()) {
            when (json.nextName()) {
                "swipeLinks" -> left = json.nextString()
                "swipeRechts" -> right = json.nextString()
                "swipeOben" -> up = json.nextString()
                "oldestFirst" -> s = s.copy(oldestFirst = json.nextBoolean())
                "radius" -> s = s.copy(clusterRadiusMeters = json.nextInt())
                "photos" -> s = s.copy(showPhotos = json.nextBoolean())
                "videos" -> s = s.copy(showVideos = json.nextBoolean())
                "screenshots" -> s = s.copy(showScreenshots = json.nextBoolean())
                "excludedAlbums" -> {
                    val albums = LinkedHashSet<String>()
                    json.beginArray()
                    while (json.hasNext()) albums.add(json.nextString())
                    json.endArray()
                    s = s.copy(excludedAlbums = albums)
                }
                "favoriteAlbums" -> {
                    val albums = LinkedHashSet<String>()
                    json.beginArray()
                    while (json.hasNext()) albums.add(json.nextString())
                    json.endArray()
                    s = s.copy(favoriteAlbums = albums)
                }
                "theme" -> {
                    val name = json.nextString()
                    s = s.copy(themeMode = ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.SYSTEM)
                }
                "haptics" -> s = s.copy(haptics = json.nextBoolean())
                "videoSound" -> s = s.copy(videoSoundDefault = json.nextBoolean())
                "infoLine" -> s = s.copy(showInfoLine = json.nextBoolean())
                else -> json.skipValue()
            }
        }
        json.endObject()
        return s.copy(swipeMapping = SwipeMapping.fromNames(left, right, up))
    }

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
                    else -> if (json.peek() == JsonToken.NULL) json.nextNull() else json.skipValue()
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

    private companion object {
        const val APP_ID = "PixelSweeper"

        /** So hieß die App vor der Umbenennung; ältere Sicherungen bleiben lesbar. */
        const val LEGACY_APP_ID = "Fotosortierer"
        const val FORMAT_VERSION = 1
    }
}
