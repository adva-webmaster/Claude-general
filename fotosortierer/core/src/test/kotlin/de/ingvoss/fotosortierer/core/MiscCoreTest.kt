package de.ingvoss.fotosortierer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaClassifierTest {
    @Test
    fun videoIstVideo() {
        assertEquals(MediaKind.VIDEO, MediaClassifier.kindOf(3, "DCIM/Camera/", "VID_1.mp4"))
    }

    @Test
    fun screenshotWirdAnPfadOderNamenErkannt() {
        assertEquals(MediaKind.SCREENSHOT, MediaClassifier.kindOf(1, "Pictures/Screenshots/", "a.png"))
        assertEquals(MediaKind.SCREENSHOT, MediaClassifier.kindOf(1, "DCIM/", "Screenshot_2026.png"))
        assertEquals(MediaKind.FOTO, MediaClassifier.kindOf(1, "DCIM/Camera/", "IMG_1.jpg"))
    }

    @Test
    fun audioUndAnderesWirdIgnoriert() {
        assertNull(MediaClassifier.kindOf(2, null, null))
        assertNull(MediaClassifier.kindOf(0, null, null))
    }

    @Test
    fun verzeichnisWirdVereinheitlicht() {
        assertEquals("DCIM/Camera/", MediaClassifier.normalizePath("DCIM/Camera/"))
        assertEquals("DCIM/Camera/", MediaClassifier.normalizePath("/DCIM/Camera"))
        assertEquals("Pictures/", MediaClassifier.normalizePath(" Pictures "))
        assertEquals("", MediaClassifier.normalizePath(null))
        assertEquals("", MediaClassifier.normalizePath("/"))
        assertEquals("", MediaClassifier.normalizePath(""))
    }

    @Test
    fun albumnameFallbacks() {
        assertEquals("Camera", MediaClassifier.albumOf("Camera", "DCIM/Other/"))
        assertEquals("Other", MediaClassifier.albumOf(null, "DCIM/Other/"))
        assertEquals("Other", MediaClassifier.albumOf("  ", "DCIM/Other/"))
        assertEquals(MediaClassifier.UNKNOWN_ALBUM, MediaClassifier.albumOf(null, null))
        assertEquals(MediaClassifier.UNKNOWN_ALBUM, MediaClassifier.albumOf(null, "/"))
    }
}

class PlaceNamesTest {
    @Test
    fun stadtteilUndStadt() {
        assertEquals("Gaarden, Kiel", PlaceNames.compose("Gaarden", "Kiel", "Schleswig-Holstein", "Deutschland"))
    }

    @Test
    fun gleicherStadtteilUndStadtWirdNichtDoppelt() {
        assertEquals("Kiel", PlaceNames.compose("Kiel", "Kiel", null, null))
    }

    @Test
    fun nurStadt() {
        assertEquals("Kiel", PlaceNames.compose(null, "Kiel", "SH", "Deutschland"))
    }

    @Test
    fun ohneStadtDienenRegionUndLand() {
        assertEquals("Bayern, Deutschland", PlaceNames.compose(null, null, "Bayern", "Deutschland"))
    }

    @Test
    fun allesLeerLiefertNull() {
        assertNull(PlaceNames.compose(" ", null, "", null))
    }

    @Test
    fun cacheSchluesselRundetAufDreiNachkommastellen() {
        assertEquals("54.323,10.123", PlaceNames.cacheKey(54.32349, 10.12251))
        assertEquals(PlaceNames.cacheKey(54.3231, 10.1229), PlaceNames.cacheKey(54.3234, 10.1226))
    }
}

class ScreenClustererTest {
    @Test
    fun nahePunkteWerdenZusammengefasst() {
        val result = ScreenClusterer.cluster(
            listOf(ScreenItem(1, 100f, 100f), ScreenItem(2, 110f, 105f), ScreenItem(3, 400f, 400f)),
            radiusPx = 50f
        )
        assertEquals(2, result.size)
        assertEquals(listOf(1L, 2L), result[0].ids)
        assertEquals(105f, result[0].x, 0.001f)
        assertEquals(listOf(3L), result[1].ids)
    }

    @Test
    fun rasterGrenzeSpieltKeineRolle() {
        // Beide Punkte liegen in verschiedenen Rasterzellen, aber näher als der Radius.
        val result = ScreenClusterer.cluster(listOf(ScreenItem(1, 49f, 10f), ScreenItem(2, 51f, 10f)), 50f)
        assertEquals(1, result.size)
    }

    @Test
    fun negativeKoordinatenFunktionieren() {
        val result = ScreenClusterer.cluster(listOf(ScreenItem(1, -5f, -5f), ScreenItem(2, 5f, 5f)), 50f)
        assertEquals(1, result.size)
    }

    @Test
    fun leereEingabe() {
        assertTrue(ScreenClusterer.cluster(emptyList(), 10f).isEmpty())
    }

    @Test
    fun vieleOrteSindSchnell() {
        val items = List(20_000) { ScreenItem(it.toLong(), (it % 200) * 7f, (it / 200) * 7f) }
        val start = System.nanoTime()
        val result = ScreenClusterer.cluster(items, 40f)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("dauerte $ms ms", ms < 2000)
        assertEquals(20_000, result.sumOf { it.ids.size })
    }
}

class FormatsTest {
    @Test
    fun bytesMitDeutschemKomma() {
        assertEquals("0 B", Formats.bytes(0))
        assertEquals("512 B", Formats.bytes(512))
        assertEquals("1,5 KB", Formats.bytes(1536))
        assertEquals("1,5 MB", Formats.bytes(1_572_864))
        assertEquals("120 MB", Formats.bytes(120L * 1024 * 1024))
        assertEquals("2,0 GB", Formats.bytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun dauer() {
        assertEquals("0:07", Formats.duration(7_000))
        assertEquals("1:05", Formats.duration(65_000))
        assertEquals("1:02:03", Formats.duration(3_723_000))
        assertEquals("0:00", Formats.duration(-5))
    }

    @Test
    fun kompakteZahl() {
        assertEquals("999", Formats.compactCount(999))
        assertEquals("1,2k", Formats.compactCount(1200))
        assertEquals("25k", Formats.compactCount(25_000))
    }
}

class BackupPlannerTest {
    private fun known(uid: Long, id: Long, name: String, taken: Long, size: Long, volume: String = "external_primary") =
        KnownMedia(
            uid, volume, id, MediaStatus.UNBEARBEITET, MediaKind.FOTO, name, "Camera", size, 0, taken,
            "2026-03-01", "2026-03"
        )

    private fun item(id: Long, name: String, taken: Long, size: Long, status: MediaStatus, volume: String = "external_primary") =
        BackupItem(volume, id, name, taken, size, status, 99)

    @Test
    fun zuordnungUeberFingerabdruckAuchBeiGeaenderterId() {
        val plan = BackupPlanner.plan(
            listOf(item(1, "a.jpg", 1000, 10, MediaStatus.BEHALTEN)),
            listOf(known(7, 555, "a.jpg", 1000, 10))
        )
        assertEquals(listOf(BackupMatch(7, MediaStatus.BEHALTEN, 99)), plan.matches)
        assertEquals(0, plan.unmatched)
    }

    @Test
    fun mehrdeutigerFingerabdruckEntscheidetPerId() {
        val plan = BackupPlanner.plan(
            listOf(item(2, "a.jpg", 1000, 10, MediaStatus.PAPIERKORB)),
            listOf(known(1, 1, "a.jpg", 1000, 10), known(2, 2, "a.jpg", 1000, 10))
        )
        assertEquals(listOf(BackupMatch(2, MediaStatus.PAPIERKORB, 99)), plan.matches)
    }

    @Test
    fun mehrdeutigOhneIdTrefferBleibtUnzugeordnet() {
        val plan = BackupPlanner.plan(
            listOf(item(9, "a.jpg", 1000, 10, MediaStatus.BEHALTEN)),
            listOf(known(1, 1, "a.jpg", 1000, 10), known(2, 2, "a.jpg", 1000, 10))
        )
        assertTrue(plan.matches.isEmpty())
        assertEquals(1, plan.unmatched)
    }

    @Test
    fun einMediumWirdNurEinmalVergeben() {
        val plan = BackupPlanner.plan(
            listOf(
                item(1, "a.jpg", 1000, 10, MediaStatus.BEHALTEN),
                item(1, "a.jpg", 1000, 10, MediaStatus.PAPIERKORB)
            ),
            listOf(known(1, 1, "a.jpg", 1000, 10))
        )
        assertEquals(1, plan.matches.size)
        assertEquals(1, plan.unmatched)
    }

    @Test
    fun unbekanntesMediumWirdGezaehlt() {
        val plan = BackupPlanner.plan(listOf(item(1, "x.jpg", 1, 1, MediaStatus.BEHALTEN)), emptyList())
        assertEquals(1, plan.unmatched)
    }
}
