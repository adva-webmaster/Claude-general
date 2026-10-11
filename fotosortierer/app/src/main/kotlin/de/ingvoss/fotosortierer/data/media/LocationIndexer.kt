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
import kotlinx.coroutines.sync.Semaphore
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
    suspend fun run() {
        if (MediaAccess.level(context) == AccessLevel.NONE || !MediaAccess.hasLocationAccess(context)) return
        val dao = db.mediaDao()
        val radius = settings.current().clusterRadiusMeters.toDouble()
        val limiter = Semaphore(PARALLEL_READS)
        while (true) {
            val jobs = dao.locationJobs(BATCH)
            if (jobs.isEmpty()) break
            val results = coroutineScope {
                jobs.map { job ->
                    async(Dispatchers.IO) {
                        limiter.withPermit { job to reader.read(job.volume, job.mediaStoreId, MediaKind.fromCode(job.kind)) }
                    }
                }.awaitAll()
            }
            db.withTransaction {
                for ((job, position) in results) {
                    if (position != null) dao.setLocation(job.uid, position.latitude, position.longitude)
                    else dao.markLocationChecked(job.uid)
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
        const val PARALLEL_READS = 4
    }
}
