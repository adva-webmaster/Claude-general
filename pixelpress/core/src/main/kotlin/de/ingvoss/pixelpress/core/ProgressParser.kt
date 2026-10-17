package de.ingvoss.pixelpress.core

/**
 * Wertet den Fortschritt von `ffmpeg -progress pipe:1` aus (Zeilen "key=value", Blöcke enden mit "progress=continue"
 * oder "progress=end"). `out_time_us` (und das gleichbedeutende, falsch benannte `out_time_ms`) sind Mikrosekunden.
 */
class ProgressParser {
    var outTimeMs: Long = 0
        private set

    var speed: Double? = null
        private set

    var ended: Boolean = false
        private set

    fun feed(line: String) {
        val index = line.indexOf('=')
        if (index <= 0) return
        val key = line.substring(0, index).trim()
        val value = line.substring(index + 1).trim()
        when (key) {
            "out_time_us", "out_time_ms" -> value.toLongOrNull()?.let { if (it >= 0) outTimeMs = it / 1000 }
            "speed" -> speed = value.removeSuffix("x").trim().toDoubleOrNull()
            "progress" -> ended = value == "end"
        }
    }

    /** Anteil 0..1 bei bekannter Gesamtdauer, sonst null. */
    fun fraction(totalMs: Long?): Float? {
        if (totalMs == null || totalMs <= 0) return null
        return (outTimeMs.toDouble() / totalMs).coerceIn(0.0, 1.0).toFloat()
    }
}
