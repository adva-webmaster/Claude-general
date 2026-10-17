package de.ingvoss.fotosortierer.data.media

import android.content.Context
import de.ingvoss.fotosortierer.core.MediaStatus
import de.ingvoss.fotosortierer.data.db.AppDatabase
import de.ingvoss.fotosortierer.data.db.MediaStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Entfernt Medien aus der Datenbank, deren Datei auf anderem Weg gelöscht wurde (z. B. mit einem Dateimanager),
 * obwohl der MediaStore sie noch listet. Wird ausgelöst, wenn eine Vorschau nicht lädt oder das Öffnen scheitert;
 * der Abgleich beim App-Start ([MediaSyncer]) räumt zusätzlich alle auf einmal auf.
 */
class MissingMediaCleaner(private val context: Context, private val db: AppDatabase) {
    enum class Existence { PRESENT, MISSING, UNKNOWN }

    private val _purged = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Meldet, dass Medien entfernt wurden (z. B. damit die Sortieransicht ihre Warteschlange neu lädt). */
    val purged: SharedFlow<Unit> = _purged

    /**
     * Öffnet die Datei lesend. "Fehlt" nur bei eindeutigem Befund (Datei bzw. MediaStore-Eintrag nicht vorhanden);
     * fehlende Rechte oder andere Fehler sind [Existence.UNKNOWN] und führen nie zum Entfernen.
     */
    fun existence(media: MediaStateEntity): Existence = try {
        val descriptor = context.contentResolver.openFileDescriptor(media.contentUri(), "r")
        if (descriptor == null) {
            Existence.UNKNOWN
        } else {
            descriptor.close()
            Existence.PRESENT
        }
    } catch (e: FileNotFoundException) {
        if (isNotFound(e.message)) Existence.MISSING else Existence.UNKNOWN
    } catch (e: SecurityException) {
        Existence.UNKNOWN
    } catch (e: IOException) {
        Existence.UNKNOWN
    } catch (e: IllegalArgumentException) {
        Existence.UNKNOWN
    }

    /**
     * Entfernt das Medium, wenn seine Datei eindeutig fehlt.
     * @return true, wenn das Medium nicht (mehr) in der Datenbank steht.
     */
    suspend fun purgeIfMissing(uid: Long): Boolean = withContext(Dispatchers.IO) {
        val media = db.mediaDao().byUid(uid) ?: return@withContext true
        if (media.mediaStatus == MediaStatus.GELOESCHT) return@withContext false
        if (existence(media) != Existence.MISSING) return@withContext false
        withContext(NonCancellable) {
            db.mediaDao().deleteByUids(listOf(uid))
            db.placeDao().deleteEmpty()
        }
        _purged.tryEmit(Unit)
        true
    }

    private fun isNotFound(message: String?): Boolean {
        val text = message?.lowercase() ?: return false
        return NOT_FOUND_HINTS.any { it in text }
    }

    private companion object {
        /** Meldungen von MediaProvider/Dateisystem für "nicht vorhanden" (ENOENT) im Gegensatz zu "keine Rechte". */
        val NOT_FOUND_HINTS = listOf("enoent", "no such file", "no item at", "not found")
    }
}
