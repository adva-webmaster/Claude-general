package de.ingvoss.fotosortierer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusAggregationTest {

    private fun counts(
        u: Int = 0, b: Int = 0, p: Int = 0, z: Int = 0, g: Int = 0
    ) = StatusCounts(unbearbeitet = u, behalten = b, papierkorb = p, zurueckgestellt = z, geloescht = g)

    @Test
    fun leereGruppeIstLeer() {
        assertEquals(ProgressState.LEER, StatusCounts.EMPTY.state)
        assertEquals(0f, StatusCounts.EMPTY.fraction, 0f)
    }

    @Test
    fun nurUnbearbeiteteSindUnbearbeitet() {
        assertEquals(ProgressState.UNBEARBEITET, counts(u = 5).state)
    }

    @Test
    fun zurueckgestellteZaehlenNichtAlsBearbeitet() {
        val c = counts(z = 3)
        assertEquals(ProgressState.UNBEARBEITET, c.state)
        assertEquals(0, c.processed)
        assertTrue(c.hasDeferred)
    }

    @Test
    fun einBearbeitetesUndEinOffenesIstTeilweise() {
        assertEquals(ProgressState.TEILWEISE, counts(u = 1, b = 1).state)
        assertEquals(ProgressState.TEILWEISE, counts(z = 1, b = 1).state)
        assertEquals(ProgressState.TEILWEISE, counts(z = 1, p = 1).state)
        assertEquals(ProgressState.TEILWEISE, counts(u = 1, g = 1).state)
    }

    @Test
    fun keinOffenesIstVollstaendig() {
        assertEquals(ProgressState.VOLLSTAENDIG, counts(b = 2, p = 1).state)
        assertEquals(ProgressState.VOLLSTAENDIG, counts(b = 1, g = 4).state)
    }

    @Test
    fun alleGeloeschtZaehlenAlsVollstaendig() {
        assertEquals(ProgressState.VOLLSTAENDIG, counts(g = 7).state)
    }

    @Test
    fun zaehlerUndAnteil() {
        val c = counts(u = 2, b = 3, p = 1, z = 2, g = 2)
        assertEquals(10, c.total)
        assertEquals(6, c.processed)
        assertEquals(4, c.open)
        assertEquals(0.6f, c.fraction, 0.0001f)
    }

    @Test
    fun monateLassenSichZuJahrenAddieren() {
        val januar = counts(b = 4)                 // vollständig
        val februar = counts(u = 1, b = 1)         // teilweise
        val jahr = januar + februar
        assertEquals(ProgressState.TEILWEISE, jahr.state)
        assertEquals(counts(u = 1, b = 5), jahr)
    }

    @Test
    fun ofZaehltStatusListe() {
        val c = StatusCounts.of(
            listOf(MediaStatus.BEHALTEN, MediaStatus.BEHALTEN, MediaStatus.ZURUECKGESTELLT, MediaStatus.GELOESCHT)
        )
        assertEquals(counts(b = 2, z = 1, g = 1), c)
        assertEquals(2, c.count(MediaStatus.BEHALTEN))
    }

    /** Erschöpfender Vergleich mit der Definition aus der Aufgabenstellung. */
    @Test
    fun alleKombinationenEntsprechenDerSpezifikation() {
        for (u in 0..2) for (b in 0..2) for (p in 0..1) for (z in 0..2) for (g in 0..1) {
            val c = counts(u, b, p, z, g)
            val anyProcessed = b + p + g > 0
            val anyOpen = u + z > 0
            val expected = when {
                c.total == 0 -> ProgressState.LEER
                !anyOpen -> ProgressState.VOLLSTAENDIG
                !anyProcessed -> ProgressState.UNBEARBEITET
                else -> ProgressState.TEILWEISE
            }
            assertEquals("u=$u b=$b p=$p z=$z g=$g", expected, c.state)
        }
    }

    @Test
    fun statusFlagsStimmen() {
        assertFalse(MediaStatus.UNBEARBEITET.isProcessed)
        assertFalse(MediaStatus.ZURUECKGESTELLT.isProcessed)
        assertTrue(MediaStatus.BEHALTEN.isProcessed)
        assertTrue(MediaStatus.PAPIERKORB.isProcessed)
        assertTrue(MediaStatus.GELOESCHT.isProcessed)
        assertTrue(MediaStatus.UNBEARBEITET.isOpen)
        assertTrue(MediaStatus.ZURUECKGESTELLT.isOpen)
        assertFalse(MediaStatus.GELOESCHT.isOpen)
        for (s in MediaStatus.entries) assertEquals(s, MediaStatus.fromCode(s.code))
        for (k in MediaKind.entries) assertEquals(k, MediaKind.fromCode(k.code))
    }
}
