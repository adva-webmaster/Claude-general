package de.ingvoss.fotosortierer.core

import java.time.LocalDate
import java.time.YearMonth

/** Berechnet Kalenderraster (Montag zuerst) für die Tagesansicht. */
object MonthLayout {
    /**
     * Zellen eines Monatsrasters mit 7 Spalten: führende `null`-Zellen bis zum Wochentag des Ersten,
     * alle Tage des Monats, dann `null`-Zellen bis zur vollen Woche.
     */
    fun cells(month: YearMonth): List<LocalDate?> {
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - 1 // Montag = 1 → 0 führende Zellen
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val result = ArrayList<LocalDate?>(42)
        repeat(leading) { result.add(null) }
        result.addAll(days)
        while (result.size % 7 != 0) result.add(null)
        return result
    }

    fun months(year: Int): List<YearMonth> = (1..12).map { YearMonth.of(year, it) }
}
