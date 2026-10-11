package de.ingvoss.fotosortierer.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthLayoutTest {
    @Test
    fun maerz2026BeginntAmSonntag() {
        val cells = MonthLayout.cells(YearMonth.of(2026, 3)) // 1. März 2026 = Sonntag
        assertEquals(DayOfWeek.SUNDAY, LocalDate.of(2026, 3, 1).dayOfWeek)
        assertEquals(42, cells.size) // 6 + 31 = 37, aufgefüllt auf 6 volle Wochen
        assertEquals(List(6) { null }, cells.take(6))
        assertEquals(LocalDate.of(2026, 3, 1), cells[6])
        assertEquals(LocalDate.of(2026, 3, 31), cells.last { it != null })
    }

    @Test
    fun monatBeginntAmMontagOhneFuehrendeZellen() {
        val cells = MonthLayout.cells(YearMonth.of(2026, 6)) // 1. Juni 2026 = Montag
        assertEquals(LocalDate.of(2026, 6, 1), cells.first())
        assertEquals(35, cells.size)
    }

    @Test
    fun schaltjahrFebruarMitVollenWochen() {
        val cells = MonthLayout.cells(YearMonth.of(2024, 2)) // 1. Feb 2024 = Donnerstag, 29 Tage
        assertEquals(35, cells.size)
        assertEquals(29, cells.count { it != null })
        assertNull(cells[0])
        assertEquals(LocalDate.of(2024, 2, 1), cells[3])
    }

    @Test
    fun jedeZeileHatSiebenZellenUndAlleTageKommenEinmalVor() {
        for (year in 2020..2027) for (month in 1..12) {
            val ym = YearMonth.of(year, month)
            val cells = MonthLayout.cells(ym)
            assertEquals(0, cells.size % 7)
            assertTrue(cells.size in 28..42)
            assertEquals(ym.lengthOfMonth(), cells.count { it != null })
            val days = cells.filterNotNull()
            assertEquals(days.sorted(), days)
            // Der Erste liegt in der Spalte seines Wochentags (Montag = 0)
            assertEquals(ym.atDay(1).dayOfWeek.value - 1, cells.indexOf(ym.atDay(1)))
        }
    }

    @Test
    fun zwoelfMonateProJahr() {
        assertEquals(12, MonthLayout.months(2026).size)
        assertEquals(YearMonth.of(2026, 12), MonthLayout.months(2026).last())
    }
}
