package de.ingvoss.fotosortierer.data.media

import de.ingvoss.fotosortierer.core.PlaceNames
import de.ingvoss.fotosortierer.data.db.AppDatabase
import de.ingvoss.fotosortierer.data.db.GeocodeCacheEntity
import de.ingvoss.fotosortierer.data.db.PlaceEntity

/** Benennt Ortscluster per Reverse-Geocoding; Ergebnisse (auch "kein Name") werden in Room gecacht. */
class PlaceNamer(private val db: AppDatabase, private val geocoder: GeocodingService) {

    /** @return true, wenn alle Orte benannt sind; false, wenn der Lauf wegen eines Netzfehlers abbrach. */
    suspend fun run(): Boolean {
        val dao = db.placeDao()
        while (true) {
            val pending = dao.pending(BATCH)
            if (pending.isEmpty()) return true
            for (place in pending) {
                val key = PlaceNames.cacheKey(place.latitude, place.longitude)
                val cached = dao.cached(key)
                if (cached != null) {
                    apply(dao, place, cached.name)
                    continue
                }
                when (val result = geocoder.resolve(place.latitude, place.longitude)) {
                    is GeocodeResult.Found -> {
                        dao.putCache(GeocodeCacheEntity(key, result.name, System.currentTimeMillis()))
                        apply(dao, place, result.name)
                    }
                    GeocodeResult.NoResult -> {
                        dao.putCache(GeocodeCacheEntity(key, null, System.currentTimeMillis()))
                        apply(dao, place, null)
                    }
                    GeocodeResult.NetworkError -> return false
                }
            }
        }
    }

    private suspend fun apply(dao: de.ingvoss.fotosortierer.data.db.PlaceDao, place: PlaceEntity, name: String?) {
        if (name != null) dao.setName(place.clusterId, name, PlaceEntity.NAME_RESOLVED)
        else dao.setName(place.clusterId, null, PlaceEntity.NAME_UNAVAILABLE)
    }

    private companion object {
        const val BATCH = 20
    }
}
