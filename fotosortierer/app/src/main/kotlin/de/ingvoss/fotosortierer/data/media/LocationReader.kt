package de.ingvoss.fotosortierer.data.media

import android.content.ContentUris
import android.content.Context
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import de.ingvoss.fotosortierer.core.Iso6709
import de.ingvoss.fotosortierer.core.LatLon
import de.ingvoss.fotosortierer.core.MediaKind

/**
 * Liest die GPS-Position eines Mediums aus den Originaldaten (EXIF bei Fotos, ISO-6709-Metadaten bei
 * Videos). Dafür ist ACCESS_MEDIA_LOCATION nötig; ohne sie würde MediaStore die Position schwärzen.
 */
class LocationReader(private val context: Context) {

    /** null = keine (gültige) Position gefunden oder Datei nicht lesbar. */
    fun read(volume: String, mediaStoreId: Long, kind: MediaKind): LatLon? {
        return try {
            val base = if (kind == MediaKind.VIDEO) {
                MediaStore.Video.Media.getContentUri(volume)
            } else {
                MediaStore.Images.Media.getContentUri(volume)
            }
            val original = MediaStore.setRequireOriginal(ContentUris.withAppendedId(base, mediaStoreId))
            if (kind == MediaKind.VIDEO) readVideo(original) else readImage(original)
        } catch (e: Exception) {
            // Datei verschwunden, beschädigt oder Berechtigung entzogen: ohne Position weiterarbeiten.
            Log.d(TAG, "Position nicht lesbar: $mediaStoreId (${e.javaClass.simpleName})")
            null
        }
    }

    private fun readImage(uri: Uri): LatLon? {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val out = FloatArray(2)
            if (exif.getLatLong(out)) {
                return LatLon(out[0].toDouble(), out[1].toDouble()).takeIf { it.isValid }
            }
        }
        return null
    }

    private fun readVideo(uri: Uri): LatLon? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            return Iso6709.parse(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))
        } finally {
            retriever.release()
        }
    }

    private companion object {
        const val TAG = "LocationReader"
    }
}
