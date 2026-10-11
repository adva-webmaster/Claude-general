package de.ingvoss.fotosortierer.data.media

import android.content.Context
import android.provider.MediaStore
import de.ingvoss.fotosortierer.core.CaptureTime
import de.ingvoss.fotosortierer.core.MediaClassifier
import de.ingvoss.fotosortierer.core.ScannedMedia
import java.time.ZoneId

/** Liest Fotos und Videos aller Volumes aus dem MediaStore (ohne Kopien, ohne EXIF). */
class MediaStoreScanner(private val context: Context) {

    /** Namen der aktuell eingehängten Volumes, z. B. "external_primary" und die SD-Karte. */
    fun volumes(): Set<String> = MediaStore.getExternalVolumeNames(context)

    /**
     * Scannt ein Volume. [onProgress] wird gelegentlich mit der bisherigen Anzahl aufgerufen.
     * @throws SecurityException wenn die Berechtigung fehlt.
     */
    fun scan(volume: String, zone: ZoneId, onProgress: (Int) -> Unit = {}): List<ScannedMedia> {
        return try {
            query(volume, zone, withDuration = true, onProgress = onProgress)
        } catch (e: IllegalArgumentException) {
            // Sehr alte/abweichende MediaProvider kennen die Spalte DURATION in der Files-Tabelle nicht.
            query(volume, zone, withDuration = false, onProgress = onProgress)
        }
    }

    private fun query(volume: String, zone: ZoneId, withDuration: Boolean, onProgress: (Int) -> Unit): List<ScannedMedia> {
        val collection = MediaStore.Files.getContentUri(volume)
        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.MEDIA_TYPE)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            add(MediaStore.MediaColumns.RELATIVE_PATH)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.DATE_ADDED)
            if (withDuration) add(MediaStore.MediaColumns.DURATION)
        }.toTypedArray()
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            MediaClassifier.MEDIA_TYPE_IMAGE.toString(),
            MediaClassifier.MEDIA_TYPE_VIDEO.toString()
        )
        val result = ArrayList<ScannedMedia>()
        context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            val iId = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val iType = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val iName = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iBucket = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val iPath = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val iSize = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iTaken = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val iModified = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val iAdded = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val iDuration = if (withDuration) cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION) else -1
            while (cursor.moveToNext()) {
                val name = cursor.getString(iName) ?: ""
                val path = cursor.getString(iPath)
                val kind = MediaClassifier.kindOf(cursor.getInt(iType), path, name) ?: continue
                val time = CaptureTime.resolve(
                    dateTakenMs = cursor.getLong(iTaken),
                    dateModifiedSec = cursor.getLong(iModified),
                    dateAddedSec = cursor.getLong(iAdded),
                    zone = zone
                )
                result.add(
                    ScannedMedia(
                        volume = volume,
                        mediaStoreId = cursor.getLong(iId),
                        kind = kind,
                        displayName = name,
                        album = MediaClassifier.albumOf(cursor.getString(iBucket), path),
                        sizeBytes = cursor.getLong(iSize),
                        durationMs = if (iDuration >= 0) cursor.getLong(iDuration) else 0L,
                        time = time
                    )
                )
                if (result.size % PROGRESS_STEP == 0) onProgress(result.size)
            }
        }
        onProgress(result.size)
        return result
    }

    private companion object {
        const val PROGRESS_STEP = 2000
    }
}
