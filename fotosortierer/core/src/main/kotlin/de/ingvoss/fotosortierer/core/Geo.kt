package de.ingvoss.fotosortierer.core

import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    const val EARTH_RADIUS_METERS = 6_371_008.8
    const val METERS_PER_DEGREE_LAT = 111_320.0

    /** Großkreisdistanz in Metern (Haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    /** Anzeige als Koordinatenbezeichnung, z. B. "54,3233° N, 10,1228° O". */
    fun formatCoordinates(lat: Double, lon: Double): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "O" else "W"
        return String.format(java.util.Locale.GERMANY, "%.4f° %s, %.4f° %s", abs(lat), ns, abs(lon), ew)
    }
}

/** Ein Medium mit Position. [id] ist frei wählbar (z. B. die Datenbank-uid). */
data class GeoPoint(val id: Long, val lat: Double, val lon: Double)

/** Ergebnis-Cluster: Schwerpunkt der Mitglieder und Anzahl der Punkte, die dieser Lauf zugeordnet hat. */
data class GeoCluster(val id: Long, val lat: Double, val lon: Double, val count: Int)

/**
 * Räumliches Clustering per "Leader"-Verfahren mit Raster-Index:
 *  - Jeder Cluster hat einen Leader (erster Punkt bzw. Startposition eines vorhandenen Clusters).
 *  - Ein neuer Punkt tritt dem NÄCHSTEN Leader bei, dessen Entfernung höchstens [radiusMeters] beträgt;
 *    gibt es keinen, entsteht ein neuer Cluster.
 *  - Der Schwerpunkt (für Anzeige/Geocoding) ist der Mittelwert der zugeordneten Punkte.
 * Deterministisch für eine gegebene Eingabereihenfolge, Laufzeit ≈ linear. Vorhandene Cluster
 * ([seed]) behalten ihre IDs; neue IDs beginnen bei [firstNewId].
 *
 * Bekannte Grenze: Der Datumssprung bei ±180° Länge wird nicht überbrückt.
 */
class GeoClusterer(
    private val radiusMeters: Double,
    seed: List<GeoCluster> = emptyList(),
    firstNewId: Long = 1L
) {
    private class Leader(val id: Long, val lat: Double, val lon: Double, var count: Int, var sumLat: Double, var sumLon: Double)

    private val leaders = ArrayList<Leader>()

    /** Zeilenraster nach Breitengrad; je Zeile sortiert nach Länge. */
    private val rows = HashMap<Int, TreeMap<Double, MutableList<Leader>>>()
    private val rowHeightDeg = radiusMeters / Geo.METERS_PER_DEGREE_LAT
    private var nextId = firstNewId

    init {
        require(radiusMeters > 0) { "Radius muss positiv sein" }
        for (s in seed) {
            val leader = Leader(s.id, s.lat, s.lon, 0, 0.0, 0.0)
            leaders.add(leader)
            index(leader)
            if (s.id >= nextId) nextId = s.id + 1
        }
    }

    private fun rowOf(lat: Double): Int = floor(lat / rowHeightDeg).toInt()

    private fun index(leader: Leader) {
        rows.getOrPut(rowOf(leader.lat)) { TreeMap() }.getOrPut(leader.lon) { ArrayList(1) }.add(leader)
    }

    /** Ordnet den Punkt zu und liefert die Cluster-ID. */
    fun add(point: GeoPoint): Long {
        val row = rowOf(point.lat)
        var best: Leader? = null
        var bestDistance = Double.MAX_VALUE
        for (r in row - 1..row + 1) {
            val tree = rows[r] ?: continue
            // Längengrad-Spanne, die den Radius bei der (betragsmäßig) größten Breite der Zeile abdeckt.
            val maxAbsLat = max(abs(r * rowHeightDeg), abs((r + 1) * rowHeightDeg)).coerceAtMost(89.9)
            val lonSpan = radiusMeters / (Geo.METERS_PER_DEGREE_LAT * max(cos(Math.toRadians(maxAbsLat)), 0.01))
            for (candidates in tree.subMap(point.lon - lonSpan, true, point.lon + lonSpan, true).values) {
                for (leader in candidates) {
                    val d = Geo.distanceMeters(point.lat, point.lon, leader.lat, leader.lon)
                    if (d <= radiusMeters && d < bestDistance) {
                        best = leader
                        bestDistance = d
                    }
                }
            }
        }
        val target = best ?: Leader(nextId++, point.lat, point.lon, 0, 0.0, 0.0).also {
            leaders.add(it)
            index(it)
        }
        target.count++
        target.sumLat += point.lat
        target.sumLon += point.lon
        return target.id
    }

    /** Alle Cluster mit mindestens einem in diesem Lauf zugeordneten Punkt; Schwerpunkt = Mittelwert. */
    fun clusters(): List<GeoCluster> =
        leaders.filter { it.count > 0 }
            .map { GeoCluster(it.id, it.sumLat / it.count, it.sumLon / it.count, it.count) }

    /** Alle Cluster inkl. leerer Seeds (zum Aufräumen). */
    fun allClusterIds(): List<Long> = leaders.map { it.id }

    companion object {
        /** Clustert [points] in der gegebenen Reihenfolge; liefert pointId → clusterId und die Cluster. */
        fun clusterAll(points: List<GeoPoint>, radiusMeters: Double): Pair<Map<Long, Long>, List<GeoCluster>> {
            val clusterer = GeoClusterer(radiusMeters)
            val assignment = LinkedHashMap<Long, Long>(points.size)
            for (p in points) assignment[p.id] = clusterer.add(p)
            return assignment to clusterer.clusters()
        }
    }
}
