package de.ingvoss.fotosortierer.data.media

import androidx.room.withTransaction
import de.ingvoss.fotosortierer.core.GeoCluster
import de.ingvoss.fotosortierer.core.GeoClusterer
import de.ingvoss.fotosortierer.core.GeoPoint
import de.ingvoss.fotosortierer.data.db.AppDatabase
import de.ingvoss.fotosortierer.data.db.PlaceEntity

/**
 * Bildet aus den Medienpositionen Ortscluster (Radius einstellbar, Standard 1 km). Neue Medien werden
 * inkrementell den bestehenden Clustern zugeordnet; bestehende Cluster behalten ihre IDs.
 */
class PlaceBuilder(private val db: AppDatabase) {

    /** Ordnet alle Medien mit Position, aber ohne Cluster, einem Ort zu. */
    suspend fun assignPending(radiusMeters: Double) {
        val mediaDao = db.mediaDao()
        val placeDao = db.placeDao()
        val points = mediaDao.unclusteredPoints()
        if (points.isEmpty()) return
        val seeds = placeDao.getAll().map { GeoCluster(it.clusterId, it.latitude, it.longitude, 0) }
        val existing = seeds.map { it.id }.toSet()
        val clusterer = GeoClusterer(radiusMeters, seeds)
        val members = LinkedHashMap<Long, MutableList<Long>>()
        for (p in points) {
            val clusterId = clusterer.add(GeoPoint(p.id, p.lat, p.lon))
            members.getOrPut(clusterId) { ArrayList() }.add(p.id)
        }
        db.withTransaction {
            for ((clusterId, uids) in members) {
                for (chunk in uids.chunked(UID_CHUNK)) mediaDao.assignCluster(chunk, clusterId)
            }
            val created = clusterer.clusters()
                .filter { it.id !in existing }
                .map { PlaceEntity(it.id, it.lat, it.lon, null, PlaceEntity.NAME_PENDING) }
            if (created.isNotEmpty()) placeDao.upsertAll(created)
        }
    }

    /** Bildet alle Ortscluster neu (nach Änderung des Radius). Ortsnamen kommen aus dem Cache. */
    suspend fun rebuild(radiusMeters: Double) {
        db.withTransaction {
            db.mediaDao().resetClusters()
            db.placeDao().deleteAll()
        }
        assignPending(radiusMeters)
    }

    /** Setzt Orte und Standorte vollständig zurück (Datenbank-Neuaufbau); Entscheidungen bleiben erhalten. */
    suspend fun resetAll() {
        db.withTransaction {
            db.mediaDao().resetLocations()
            db.placeDao().deleteAll()
            db.placeDao().clearCache()
        }
    }

    private companion object {
        const val UID_CHUNK = 500
    }
}
