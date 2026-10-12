package de.ingvoss.fotosortierer.data.media

import android.content.Context
import androidx.room.withTransaction
import de.ingvoss.fotosortierer.core.MediaKind
import de.ingvoss.fotosortierer.data.db.AppDatabase
import de.ingvoss.fotosortierer.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Hintergrund-Indexierung der Standorte: liest die GPS-Position aller noch ungeprüften Medien in
 * Stapeln, ordnet sie sofort Orten zu und stößt anschließend die Ortsbenennung an. Der Fortschritt
 * ergibt sich aus der Datenbank (`observeLocationProgress`), nicht aus dem Worker.
 */
class LocationIndexer(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val reader: LocationReader,
    private val placeBuilder: PlaceBuilder,
    private val onPlacesCreated: () -> Unit
) {
    /** Es läuft nie mehr als ein Durchgang gleichzeitig (Vordergrund und Worker können sich überschneiden). */
    private val runLock = Mutex()

    suspend fun run() = runLock.withLock { runLocked() }

    private suspend fun runLocked() {
        if (MediaAccess.level(context) == AccessLevel.NONE || !MediaAccess.hasLocationAccess(context)) return
        val dao = db.mediaDao()
        val radius = settings.current().clusterRadiusMeters.toDouble()
        val limiter = Semaphore(PARALLEL_READS)
        // Medien, deren Lesen fehlschlug, bleiben unmarkiert (nächster Durchgang versucht es erneut). Damit dieser
        // Durchgang nicht endlos an ihnen hängt, wandert eine Position (uid) durch die Liste.
        var afterUid = 0L
        while (true) {
            val jobs = dao.locationJobs(afterUid, BATCH)
            if (jobs.isEmpty()) break
            afterUid = jobs.last().uid
            val results = coroutineScope {
                jobs.map { job ->
                    async(Dispatchers.IO) {
                        limiter.withPermit { job to reader.read(job.volume, job.mediaStoreId, MediaKind.fromCode(job.kind)) }
                    }
                }.awaitAll()
            }
            db.withTransaction {
                for ((job, result) in results) {
                    when (result) {
                        is LocationReader.Result.Found ->
                            dao.setLocation(job.uid, result.position.latitude, result.position.longitude)
                        LocationReader.Result.None -> dao.markLocationChecked(job.uid)
                        LocationReader.Result.Failed -> Unit
                    }
                }
            }
            placeBuilder.assignPending(radius)
            currentCoroutineContext().ensureActive()
        }
        placeBuilder.assignPending(radius)
        onPlacesCreated()
    }

    private companion object {
        const val BATCH = 200
        const val PARALLEL_READS = 6
    }
}
