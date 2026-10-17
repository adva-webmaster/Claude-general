package de.ingvoss.fotosortierer.data.media

import android.content.Context
import androidx.room.withTransaction
import android.util.Log
import de.ingvoss.fotosortierer.core.LocationRetryPolicy
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
import kotlinx.coroutines.withContext

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
        var attempted = 0
        val failedUids = ArrayList<Long>()
        while (true) {
            val jobs = dao.locationJobs(afterUid, BATCH)
            if (jobs.isEmpty()) break
            afterUid = jobs.last().uid
            attempted += jobs.size
            val first = coroutineScope {
                jobs.map { job ->
                    async(Dispatchers.IO) {
                        limiter.withPermit { job to reader.read(job.volume, job.mediaStoreId, MediaKind.fromCode(job.kind)) }
                    }
                }.awaitAll()
            }
            // Fehlgeschlagene noch einmal der Reihe nach versuchen: Meist war es nur ein kurzer Aussetzer.
            val results = withContext(Dispatchers.IO) {
                first.map { (job, result) ->
                    if (result == LocationReader.Result.Failed) {
                        job to reader.read(job.volume, job.mediaStoreId, MediaKind.fromCode(job.kind))
                    } else {
                        job to result
                    }
                }
            }
            db.withTransaction {
                for ((job, result) in results) {
                    when (result) {
                        is LocationReader.Result.Found ->
                            dao.setLocation(job.uid, result.position.latitude, result.position.longitude)
                        LocationReader.Result.None -> dao.markLocationChecked(job.uid)
                        LocationReader.Result.Failed -> failedUids += job.uid
                    }
                }
            }
            placeBuilder.assignPending(radius)
            currentCoroutineContext().ensureActive()
        }
        // Wenige Dateien, die auch im zweiten Versuch nicht lesbar sind (defekt, unbekanntes Format), gelten als ohne
        // Standort – sonst bliebe die Fortschrittsanzeige für immer unvollständig. Viele Fehler deuten auf ein
        // allgemeines Problem hin; dann bleiben sie offen und der nächste Durchgang versucht es erneut.
        if (LocationRetryPolicy.giveUp(failedUids.size, attempted)) {
            Log.w(TAG, "${failedUids.size} von $attempted Medien ohne lesbare Position: werden als ohne Standort geführt")
            db.withTransaction { for (uid in failedUids) dao.markLocationChecked(uid) }
        } else if (failedUids.isNotEmpty()) {
            Log.w(TAG, "${failedUids.size} von $attempted Medien nicht lesbar: später erneut")
        }
        placeBuilder.assignPending(radius)
        onPlacesCreated()
    }

    private companion object {
        const val TAG = "LocationIndexer"
        const val BATCH = 200
        const val PARALLEL_READS = 6
    }
}
