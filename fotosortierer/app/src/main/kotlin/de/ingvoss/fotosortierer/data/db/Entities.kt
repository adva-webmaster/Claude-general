package de.ingvoss.fotosortierer.data.db

import android.net.Uri
import android.content.ContentUris
import android.provider.MediaStore
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.ingvoss.fotosortierer.core.MediaKind
import de.ingvoss.fotosortierer.core.MediaStatus
import de.ingvoss.fotosortierer.core.MediaTable

/**
 * Zustand eines Mediums. Die Medien selbst bleiben im MediaStore; hier liegen nur Verweis, kopierte
 * Metadaten für schnelle Abfragen und die Entscheidung des Nutzers. Der Aufbau muss [MediaTable.CREATE_SQL]
 * entsprechen (diese DDL benutzen die JVM-Tests im Modul :core).
 */
@Entity(
    tableName = MediaTable.NAME,
    indices = [
        Index(value = ["volume", "mediaStoreId"], unique = true),
        Index(value = ["localDate"]),
        Index(value = ["localMonth"]),
        Index(value = ["clusterId"]),
        Index(value = ["status"]),
        Index(value = ["takenAtUtc"])
    ]
)
data class MediaStateEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val mediaStoreId: Long,
    val volume: String,
    val takenAtUtc: Long,
    val localDate: String,
    val localMonth: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val clusterId: Long? = null,
    val album: String,
    val kind: Int,
    val sizeBytes: Long,
    val status: Int,
    val statusChangedAt: Long,
    val displayName: String,
    val durationMs: Long,
    val locationChecked: Boolean,
    val hidden: Boolean
) {
    val mediaStatus: MediaStatus get() = MediaStatus.fromCode(status)
    val mediaKind: MediaKind get() = MediaKind.fromCode(kind)
    val isVideo: Boolean get() = kind == MediaKind.VIDEO.code

    /** content://-Adresse des Mediums im MediaStore (volume-spezifisch). */
    fun contentUri(): Uri {
        val base = if (isVideo) MediaStore.Video.Media.getContentUri(volume) else MediaStore.Images.Media.getContentUri(volume)
        return ContentUris.withAppendedId(base, mediaStoreId)
    }
}

/**
 * Ortscluster (räumlich zusammengefasste Aufnahmeorte). Der Name wird im Hintergrund per Reverse-Geocoding
 * bestimmt; solange [nameState] = [NAME_PENDING] zeigt die Oberfläche die Koordinaten.
 */
@Entity(tableName = "place")
data class PlaceEntity(
    @PrimaryKey val clusterId: Long,
    val latitude: Double,
    val longitude: Double,
    val name: String?,
    val nameState: Int
) {
    companion object {
        const val NAME_PENDING = 0
        const val NAME_RESOLVED = 1
        /** Kein Ergebnis vom Geocoder: dauerhaft Koordinaten anzeigen. */
        const val NAME_UNAVAILABLE = 2
    }
}

/** Geocoding-Cache je ≈110-m-Rasterzelle, damit nach Radiusänderungen nicht neu abgefragt wird. */
@Entity(tableName = "geocode_cache")
data class GeocodeCacheEntity(
    @PrimaryKey val cellKey: String,
    /** null = die Auflösung ergab keinen Namen. */
    val name: String?,
    val resolvedAt: Long
)
