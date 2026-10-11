package de.ingvoss.fotosortierer.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.ingvoss.fotosortierer.FotoApp
import java.util.concurrent.TimeUnit

/** Liest im Hintergrund die GPS-Positionen aller Medien und ordnet sie Orten zu. */
class LocationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as FotoApp).container
        container.locationIndexer.run()
        return Result.success()
    }
}

/** Benennt Ortscluster per Reverse-Geocoding (braucht Netz, höchstens 1 Nominatim-Anfrage pro Sekunde). */
class GeocodeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as FotoApp).container
        return if (container.placeNamer.run()) Result.success() else Result.retry()
    }
}

/** Reiht die Hintergrundarbeiten ein. "Append" statt "Keep", damit neue Medien während eines Laufs nicht untergehen. */
class WorkScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun enqueueLocationIndexing() {
        workManager.enqueueUniqueWork(
            LOCATION_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<LocationWorker>().build()
        )
    }

    fun enqueueGeocoding() {
        val request = OneTimeWorkRequestBuilder<GeocodeWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(GEOCODE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val LOCATION_WORK = "location-index"
        const val GEOCODE_WORK = "geocode-places"
        const val BACKOFF_SECONDS = 60L
    }
}
