package de.ingvoss.fotosortierer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomPanTest {
    private val w = 1000f
    private val h = 2000f

    @Test
    fun zoomWirdAufHoechstwertBegrenzt() {
        val z = ZoomPan().transform(10f, 0f, 0f, 0f, 0f, w, h)
        assertEquals(ZoomPan.MAX_SCALE, z.scale, 0.0001f)
        assertTrue(z.isZoomed)
    }

    @Test
    fun zoomUnterEinsWirdAufEinsBegrenztUndZentriert() {
        val z = ZoomPan(2f, 300f, 0f).transform(0.1f, 0f, 0f, 0f, 0f, w, h)
        assertEquals(1f, z.scale, 0.0001f)
        assertEquals(0f, z.panX, 0.0001f)
        assertFalse(z.isZoomed)
    }

    @Test
    fun verschiebungBleibtImBild() {
        val z = ZoomPan().transform(2f, 0f, 0f, 0f, 0f, w, h).transform(1f, 0f, 0f, 900f, -5000f, w, h)
        // Bei Faktor 2 darf das Bild um höchstens (Faktor − 1) · Größe / 2 verschoben werden.
        assertEquals(500f, z.panX, 0.001f)
        assertEquals(-1000f, z.panY, 0.001f)
    }

    @Test
    fun zoomHaeltDenBeruehrpunktFest() {
        // Berührpunkt 100 px rechts der Mitte: nach Verdopplung muss die Verschiebung ihn an Ort halten.
        val z = ZoomPan().transform(2f, 100f, 0f, 0f, 0f, w, h)
        assertEquals(-100f, z.panX, 0.001f)
        // Kontrolle: Bildpunkt p landet bei mitte + scale · p + pan; für p = 100 muss das wieder 100 sein.
        assertEquals(100f, z.scale * 100f + z.panX, 0.001f)
    }

    @Test
    fun doppeltippZoomZentriertAufDenTippPunkt() {
        val z = ZoomPan().zoomedTo(2.5f, tapX = w / 2f + 100f, tapY = h / 2f, width = w, height = h)
        assertEquals(2.5f, z.scale, 0.0001f)
        // Der angetippte Punkt (100 px rechts der Mitte) bleibt an derselben Stelle.
        assertEquals(100f, z.scale * 100f + z.panX, 0.001f)
    }

    @Test
    fun doppeltippAmRandWirdBegrenzt() {
        val z = ZoomPan().zoomedTo(2.5f, tapX = w, tapY = 0f, width = w, height = h)
        assertEquals(-750f, z.panX, 0.001f) // (2,5 − 1) · 1000 / 2
        assertEquals(1500f, z.panY, 0.001f)
    }

    @Test
    fun ungezoomtesBildHatKeineVerschiebung() {
        val z = ZoomPan(1f, 50f, 50f).clamped(w, h)
        assertEquals(0f, z.panX, 0.0001f)
        assertEquals(0f, z.panY, 0.0001f)
    }
}
