package de.ingvoss.fotosortierer.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Aus welchem MediaStore-Feld die Aufnahmezeit stammt. */
enum class TimeSource { DATE_TAKEN, DATE_MODIFIED, DATE_ADDED, UNBEKANNT }

/**
 * Aufgelöste Aufnahmezeit: UTC-Millisekunden plus lokaler Tag (yyyy-MM-dd) und Monat (yyyy-MM)
 * in der verwendeten Zeitzone.
 */
data class ResolvedTime(
    val utcMillis: Long,
    val localDate: String,
    val localMonth: String,
    val source: TimeSource
)

/**
 * Bestimmt die Aufnahmezeit eines Mediums und ordnet sie einem lokalen Kalendertag zu.
 *
 * Regeln:
 *  1. DATE_TAKEN (Millisekunden, UTC), wenn > 0.
 *  2. Sonst DATE_MODIFIED (Sekunden) × 1000.
 *  3. Sonst DATE_ADDED (Sekunden) × 1000.
 *  4. Sonst Epoche 0 (Quelle UNBEKANNT).
 *
 * Manche Apps schreiben DATE_TAKEN fälschlich in Sekunden. Liegt der Wert unter
 * [SECONDS_HEURISTIC_LIMIT] (≈ 1973 in Millisekunden) und ergibt als Sekunden interpretiert ein
 * Datum ab 1990, wird er als Sekunden gelesen.
 */
object CaptureTime {
    const val SECONDS_HEURISTIC_LIMIT = 100_000_000_000L
    private const val YEAR_1990_SECONDS = 631_152_000L
    private const val MILLIS_PER_MINUTE = 60_000L

    private val dayFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val monthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    fun resolve(
        dateTakenMs: Long?,
        dateModifiedSec: Long?,
        dateAddedSec: Long?,
        zone: ZoneId,
        dayStartMinutes: Int = 0
    ): ResolvedTime {
        val taken = dateTakenMs?.takeIf { it > 0 }
        val (utc, source) = when {
            taken != null -> normalizeTaken(taken) to TimeSource.DATE_TAKEN
            dateModifiedSec != null && dateModifiedSec > 0 -> dateModifiedSec * 1000 to TimeSource.DATE_MODIFIED
            dateAddedSec != null && dateAddedSec > 0 -> dateAddedSec * 1000 to TimeSource.DATE_ADDED
            else -> 0L to TimeSource.UNBEKANNT
        }
        return ResolvedTime(utc, localDate(utc, zone, dayStartMinutes), localMonth(utc, zone, dayStartMinutes), source)
    }

    private fun normalizeTaken(value: Long): Long =
        if (value < SECONDS_HEURISTIC_LIMIT && value >= YEAR_1990_SECONDS) value * 1000 else value

    /**
     * Lokaler Kalendertag eines UTC-Zeitpunkts. [dayStartMinutes] verschiebt den Tagesbeginn
     * (z. B. 240 = Tag beginnt um 04:00 Uhr); Standard ist Mitternacht. (Noch nicht in den
     * Einstellungen verfügbar, die Architektur hält es aber offen.)
     */
    fun localDate(utcMillis: Long, zone: ZoneId, dayStartMinutes: Int = 0): String =
        shifted(utcMillis, zone, dayStartMinutes).format(dayFormat)

    fun localMonth(utcMillis: Long, zone: ZoneId, dayStartMinutes: Int = 0): String =
        shifted(utcMillis, zone, dayStartMinutes).format(monthFormat)

    private fun shifted(utcMillis: Long, zone: ZoneId, dayStartMinutes: Int): LocalDate =
        Instant.ofEpochMilli(utcMillis - dayStartMinutes * MILLIS_PER_MINUTE).atZone(zone).toLocalDate()
}
