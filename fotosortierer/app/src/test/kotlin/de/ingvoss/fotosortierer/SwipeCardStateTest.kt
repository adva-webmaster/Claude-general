package de.ingvoss.fotosortierer

import androidx.compose.ui.geometry.Offset
import de.ingvoss.fotosortierer.ui.formatTaken
import de.ingvoss.fotosortierer.ui.sort.SwipeCardState
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeCardStateTest {
    private fun state() = SwipeCardState().apply {
        widthPx = 1000f
        heightPx = 2000f
    }

    @Test
    fun zoomWirdAufHoechstwertBegrenzt() {
        val s = state()
        s.transform(zoom = 10f, centroid = Offset.Zero, pan = Offset.Zero)
        assertEquals(de.ingvoss.fotosortierer.core.ZoomPan.MAX_SCALE, s.scale, 0.0001f)
        assertTrue(s.isZoomed)
    }

    @Test
    fun zoomUnterEinsWirdAufEinsBegrenzt() {
        val s = state()
        s.transform(zoom = 0.2f, centroid = Offset.Zero, pan = Offset.Zero)
        assertEquals(1f, s.scale, 0.0001f)
        assertFalse(s.isZoomed)
    }

    @Test
    fun verschiebungBleibtImBild() {
        val s = state()
        s.transform(zoom = 2f, centroid = Offset.Zero, pan = Offset.Zero)
        s.transform(zoom = 1f, centroid = Offset.Zero, pan = Offset(900f, -5000f))
        // Bei Faktor 2 darf das Bild um höchstens (Faktor − 1) · Größe / 2 verschoben werden.
        assertEquals(500f, s.panX, 0.001f)
        assertEquals(-1000f, s.panY, 0.001f)
    }

    @Test
    fun zoomHaeltDenBerührpunktFest() {
        val s = state()
        // Berührpunkt 100 px rechts der Mitte: nach Verdopplung muss die Verschiebung ihn an Ort halten.
        s.transform(zoom = 2f, centroid = Offset(100f, 0f), pan = Offset.Zero)
        assertEquals(-100f, s.panX, 0.001f)
    }

    @Test
    fun ohneAufnahmedatumKeineInfozeile() {
        assertNull(formatTaken(0L))
        assertNull(formatTaken(-5L))
    }

    @Test
    fun aufnahmedatumWirdMitUhrzeitFormatiert() {
        val text = formatTaken(1_773_314_400_000L, ZoneId.of("UTC"))
        assertNotNull(text)
        assertTrue(text!!.contains("2026"))
        assertTrue(text.contains(":"))
    }
}
