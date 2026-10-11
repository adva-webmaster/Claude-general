package de.ingvoss.fotosortierer.core

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoClustererTest {
    @Test
    fun entfernungKielHamburg() {
        // Kiel Hbf → Hamburg Hbf ≈ 87 km
        val d = Geo.distanceMeters(54.3140, 10.1317, 53.5528, 10.0067)
        assertTrue("d=$d", d in 84_000.0..90_000.0)
        assertEquals(0.0, Geo.distanceMeters(10.0, 10.0, 10.0, 10.0), 1e-6)
    }

    @Test
    fun koordinatenBezeichnung() {
        assertEquals("54,3233° N, 10,1228° O", Geo.formatCoordinates(54.3233, 10.1228))
        assertEquals("33,8688° S, 151,2093° W", Geo.formatCoordinates(-33.8688, -151.2093))
    }

    @Test
    fun nahePunkteBildenEinenCluster() {
        val pts = (0 until 20).map { GeoPoint(it.toLong(), 54.3233 + it * 0.00001, 10.1228 + it * 0.00001) }
        val (assignment, clusters) = GeoClusterer.clusterAll(pts, 1000.0)
        assertEquals(1, clusters.size)
        assertEquals(20, clusters.single().count)
        assertEquals(1, assignment.values.toSet().size)
    }

    @Test
    fun entfernteOrteBildenGetrennteCluster() {
        val kiel = GeoPoint(1, 54.3233, 10.1228)
        val hamburg = GeoPoint(2, 53.5511, 9.9937)
        val kiel2 = GeoPoint(3, 54.3240, 10.1230)
        val (assignment, clusters) = GeoClusterer.clusterAll(listOf(kiel, hamburg, kiel2), 1000.0)
        assertEquals(2, clusters.size)
        assertEquals(assignment[1], assignment[3])
        assertNotEquals(assignment[1], assignment[2])
    }

    @Test
    fun radiusBestimmtDieGroesse() {
        // Zwei Punkte ca. 2,2 km auseinander (0,02° Breite)
        val pts = listOf(GeoPoint(1, 54.30, 10.12), GeoPoint(2, 54.32, 10.12))
        assertEquals(2, GeoClusterer.clusterAll(pts, 1000.0).second.size)
        assertEquals(1, GeoClusterer.clusterAll(pts, 3000.0).second.size)
    }

    @Test
    fun hohebreiteEngerLaengengradWirdKorrektBehandelt() {
        // Tromsø (69,65° N): 0,02° Länge ≈ 0,78 km → innerhalb 1 km; 0,05° ≈ 1,9 km → außerhalb
        val innen = listOf(GeoPoint(1, 69.65, 18.95), GeoPoint(2, 69.65, 18.97))
        assertEquals(1, GeoClusterer.clusterAll(innen, 1000.0).second.size)
        val aussen = listOf(GeoPoint(1, 69.65, 18.95), GeoPoint(2, 69.65, 19.00))
        assertEquals(2, GeoClusterer.clusterAll(aussen, 1000.0).second.size)
    }

    @Test
    fun aequatorUndNegativeKoordinaten() {
        val pts = listOf(
            GeoPoint(1, -0.0005, -0.0005),
            GeoPoint(2, 0.0005, 0.0005),   // ~157 m entfernt
            GeoPoint(3, -33.8688, 151.2093)
        )
        val (assignment, clusters) = GeoClusterer.clusterAll(pts, 500.0)
        assertEquals(2, clusters.size)
        assertEquals(assignment[1], assignment[2])
    }

    @Test
    fun punktTrittDemNaechstenLeaderBei() {
        val clusterer = GeoClusterer(1000.0)
        val a = clusterer.add(GeoPoint(1, 54.3000, 10.0000))
        val b = clusterer.add(GeoPoint(2, 54.3150, 10.0000)) // ~1,67 km → eigener Cluster
        assertNotEquals(a, b)
        // liegt im Radius beider Leader: ~0,95 km von a, ~0,72 km von b → nächster ist b
        val c = clusterer.add(GeoPoint(3, 54.3085, 10.0000))
        assertEquals(b, c)
    }

    @Test
    fun seedClusterBehaltenIhreIdUndNeueIdsLaufenWeiter() {
        val seed = listOf(GeoCluster(7, 54.3233, 10.1228, 100), GeoCluster(12, 53.5511, 9.9937, 40))
        val clusterer = GeoClusterer(1000.0, seed)
        assertEquals(7L, clusterer.add(GeoPoint(1, 54.3234, 10.1229)))
        assertEquals(12L, clusterer.add(GeoPoint(2, 53.5512, 9.9938)))
        val neu = clusterer.add(GeoPoint(3, 48.1351, 11.5820)) // München
        assertEquals(13L, neu)
        assertEquals(setOf(7L, 12L, 13L), clusterer.clusters().map { it.id }.toSet())
    }

    @Test
    fun schwerpunktIstMittelwert() {
        val clusterer = GeoClusterer(1000.0)
        clusterer.add(GeoPoint(1, 54.0000, 10.0000))
        clusterer.add(GeoPoint(2, 54.0010, 10.0020))
        val c = clusterer.clusters().single()
        assertEquals(54.0005, c.lat, 1e-9)
        assertEquals(10.0010, c.lon, 1e-9)
        assertEquals(2, c.count)
    }

    @Test
    fun deterministischBeiGleicherEingabe() {
        val rnd = Random(42)
        val pts = (0 until 2000).map {
            GeoPoint(it.toLong(), 54.0 + rnd.nextDouble() * 0.2, 10.0 + rnd.nextDouble() * 0.3)
        }
        val a = GeoClusterer.clusterAll(pts, 1000.0)
        val b = GeoClusterer.clusterAll(pts, 1000.0)
        assertEquals(a.first, b.first)
        assertEquals(a.second, b.second)
    }

    @Test
    fun alleMitgliederLiegenImRadiusDesLeaders() {
        val rnd = Random(7)
        val pts = (0 until 3000).map {
            GeoPoint(it.toLong(), 53.0 + rnd.nextDouble() * 0.1, 9.0 + rnd.nextDouble() * 0.15)
        }
        val radius = 800.0
        val (assignment, clusters) = GeoClusterer.clusterAll(pts, radius)
        // Der erste Punkt jedes Clusters ist sein Leader.
        val leaderOf = HashMap<Long, GeoPoint>()
        for (p in pts) leaderOf.getOrPut(assignment.getValue(p.id)) { p }
        for (p in pts) {
            val leader = leaderOf.getValue(assignment.getValue(p.id))
            assertTrue(Geo.distanceMeters(p.lat, p.lon, leader.lat, leader.lon) <= radius + 1e-6)
        }
        assertEquals(pts.size, clusters.sumOf { it.count })
    }

    @Test
    fun grosseMengeIstSchnell() {
        val rnd = Random(1)
        val pts = (0 until 60_000).map {
            GeoPoint(it.toLong(), 47.0 + rnd.nextDouble() * 8.0, 6.0 + rnd.nextDouble() * 9.0)
        }
        val start = System.nanoTime()
        val (assignment, clusters) = GeoClusterer.clusterAll(pts, 1000.0)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(pts.size, assignment.size)
        assertTrue(clusters.isNotEmpty())
        assertTrue("Clustering dauerte $ms ms", ms < 5_000)
    }
}
