package de.ingvoss.fotosortierer.core

import java.util.Locale

/** Zahlenformatierung mit deutschem Dezimalkomma. */
object Formats {
    private val units = listOf("B", "KB", "MB", "GB", "TB")

    /** Dateigröße, Basis 1024: "0 B", "512 B", "1,5 MB", "120 MB". */
    fun bytes(value: Long, locale: Locale = Locale.GERMANY): String {
        if (value <= 0) return "0 B"
        var v = value.toDouble()
        var unit = 0
        while (v >= 1024.0 && unit < units.lastIndex) {
            v /= 1024.0
            unit++
        }
        return when {
            unit == 0 -> "${value} B"
            v >= 100 -> String.format(locale, "%.0f %s", v, units[unit])
            else -> String.format(locale, "%.1f %s", v, units[unit])
        }
    }

    /** Videodauer: "0:07", "1:05", "1:02:03". */
    fun duration(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0) + 500) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** Kompakte Zahl für Kartenmarker: 999 → "999", 1200 → "1,2k", 25000 → "25k". */
    fun compactCount(value: Int, locale: Locale = Locale.GERMANY): String = when {
        value < 1000 -> value.toString()
        value < 10_000 -> String.format(locale, "%.1fk", value / 1000.0)
        else -> "${value / 1000}k"
    }
}
