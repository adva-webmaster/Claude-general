package de.ingvoss.fotosortierer.core

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureTimeTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val newYork = ZoneId.of("America/New_York")
    private val auckland = ZoneId.of("Pacific/Auckland")

    private fun utc(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun dateTakenHatVorrang() {
        val taken = utc("2026-03-12T10:00:00Z")
        val r = CaptureTime.resolve(taken, 1_700_000_000L, 1_600_000_000L, berlin)
        assertEquals(taken, r.utcMillis)
        assertEquals(TimeSource.DATE_TAKEN, r.source)
        assertEquals("2026-03-12", r.localDate)
        assertEquals("2026-03", r.localMonth)
    }

    @Test
    fun fallbackAufDateModifiedInSekunden() {
        val r = CaptureTime.resolve(null, 1_773_309_600L, 1_600_000_000L, berlin)
        assertEquals(1_773_309_600_000L, r.utcMillis)
        assertEquals(TimeSource.DATE_MODIFIED, r.source)
    }

    @Test
    fun nichtPositivesDateTakenGiltAlsFehlend() {
        assertEquals(TimeSource.DATE_MODIFIED, CaptureTime.resolve(0L, 1_700_000_000L, 5L, berlin).source)
        assertEquals(TimeSource.DATE_MODIFIED, CaptureTime.resolve(-5L, 1_700_000_000L, 5L, berlin).source)
    }

    @Test
    fun fallbackAufDateAddedUndDanachUnbekannt() {
        val added = CaptureTime.resolve(null, 0L, 1_700_000_000L, berlin)
        assertEquals(TimeSource.DATE_ADDED, added.source)
        assertEquals(1_700_000_000_000L, added.utcMillis)

        val none = CaptureTime.resolve(null, null, null, berlin)
        assertEquals(TimeSource.UNBEKANNT, none.source)
        assertEquals(0L, none.utcMillis)
        assertEquals("1970-01-01", none.localDate)
    }

    @Test
    fun dateTakenInSekundenWirdErkannt() {
        // 2026-03-12T10:00:00Z in Sekunden statt Millisekunden
        val seconds = utc("2026-03-12T10:00:00Z") / 1000
        val r = CaptureTime.resolve(seconds, null, null, berlin)
        assertEquals(seconds * 1000, r.utcMillis)
        assertEquals("2026-03-12", r.localDate)
    }

    @Test
    fun sehrFruehesDateTakenBleibtUnveraendert() {
        // 1_000_000 ms (1970) ist kein plausibler Sekundenwert ab 1990 → unverändert
        val r = CaptureTime.resolve(1_000_000L, null, null, berlin)
        assertEquals(1_000_000L, r.utcMillis)
    }

    @Test
    fun tagesgrenzeInBerlinWinterzeit() {
        // 23:30 UTC = 00:30 MEZ am Folgetag
        val r = CaptureTime.resolve(utc("2026-03-12T23:30:00Z"), null, null, berlin)
        assertEquals("2026-03-13", r.localDate)
        assertEquals("2026-03", r.localMonth)
    }

    @Test
    fun tagesgrenzeInBerlinSommerzeit() {
        // 22:30 UTC = 00:30 MESZ am Folgetag
        val r = CaptureTime.resolve(utc("2026-07-01T22:30:00Z"), null, null, berlin)
        assertEquals("2026-07-02", r.localDate)
        // 21:59 UTC = 23:59 MESZ am selben Tag
        assertEquals("2026-07-01", CaptureTime.localDate(utc("2026-07-01T21:59:59Z"), berlin))
        assertEquals("2026-07-02", CaptureTime.localDate(utc("2026-07-01T22:00:00Z"), berlin))
    }

    @Test
    fun monatsgrenzeUndJahreswechsel() {
        val r = CaptureTime.resolve(utc("2026-01-31T23:30:00Z"), null, null, berlin)
        assertEquals("2026-02-01", r.localDate)
        assertEquals("2026-02", r.localMonth)
        val silvester = CaptureTime.resolve(utc("2026-12-31T23:30:00Z"), null, null, berlin)
        assertEquals("2027-01-01", silvester.localDate)
        assertEquals("2027-01", silvester.localMonth)
    }

    @Test
    fun zeitumstellungsTagWirdKorrektZugeordnet() {
        // Berlin: Umstellung am 2026-03-29 um 02:00 → 03:00 (UTC+1 → UTC+2)
        assertEquals("2026-03-29", CaptureTime.localDate(utc("2026-03-29T00:59:59Z"), berlin))
        assertEquals("2026-03-29", CaptureTime.localDate(utc("2026-03-29T01:00:00Z"), berlin))
        assertEquals("2026-03-30", CaptureTime.localDate(utc("2026-03-29T22:00:00Z"), berlin))
        // Herbst: 2026-10-25 03:00 → 02:00
        assertEquals("2026-10-25", CaptureTime.localDate(utc("2026-10-24T22:00:00Z"), berlin))
        assertEquals("2026-10-25", CaptureTime.localDate(utc("2026-10-25T22:59:59Z"), berlin))
        assertEquals("2026-10-26", CaptureTime.localDate(utc("2026-10-25T23:00:00Z"), berlin))
    }

    @Test
    fun andereZeitzonen() {
        // 02:30 UTC = 22:30 EDT am Vortag
        assertEquals("2026-03-11", CaptureTime.localDate(utc("2026-03-12T02:30:00Z"), newYork))
        // 23:30 UTC = 12:30 NZDT (UTC+13 im Januar) am Folgetag
        assertEquals("2026-01-02", CaptureTime.localDate(utc("2026-01-01T23:30:00Z"), auckland))
    }

    @Test
    fun gleicherZeitpunktErgibtJeNachZoneAndereTage() {
        val t = utc("2026-05-05T23:00:00Z")
        assertEquals("2026-05-06", CaptureTime.localDate(t, berlin))
        assertEquals("2026-05-05", CaptureTime.localDate(t, newYork))
    }

    @Test
    fun konfigurierbarerTagesbeginnVerschiebtDieGrenze() {
        // Tag beginnt um 04:00 Uhr: 02:00 Uhr Ortszeit zählt noch zum Vortag
        val t = utc("2026-03-12T01:00:00Z") // 02:00 MEZ
        assertEquals("2026-03-12", CaptureTime.localDate(t, berlin, 0))
        assertEquals("2026-03-11", CaptureTime.localDate(t, berlin, 240))
        assertEquals("2026-03", CaptureTime.localMonth(utc("2026-04-01T01:00:00Z"), berlin, 240))
    }
}
