package de.ingvoss.pixelpress.core

enum class Reason {
    OK,
    INPUT_UNREADABLE,
    OUTPUT_MISSING_OR_EMPTY,
    OUTPUT_TOO_SMALL,
    OUTPUT_TOO_LARGE,
    DURATION_MISMATCH
}

/** Ergebnis der Plausibilitätsprüfung; [ratio] = Größe der MP4 geteilt durch Größe der Quelle. */
data class Verdict(val ok: Boolean, val reason: Reason, val ratio: Double?)

/**
 * Prüft vor dem Löschen der Quelle, ob die MP4 glaubhaft ist: Größe im erwartbaren Verhältnis zur Quelle und (wenn
 * bekannt) fast gleiche Länge. Nur wenn die Prüfung besteht, darf die Quelle gelöscht werden.
 */
object Plausibility {
    /** Kleiner ist nie eine brauchbare Videodatei. */
    const val MIN_OUTPUT_BYTES = 20L * 1024

    /** Beim bloßen Umpacken bleibt die Größe fast gleich. */
    val COPY_RANGE = 0.85..1.15

    /** Bei der Neuberechnung ist alles von winzig (Motion-JPEG-Quellen schrumpfen stark) bis etwas größer möglich. */
    val TRANSCODE_RANGE = 0.005..3.0

    private const val DURATION_TOLERANCE_MIN_MS = 2000L
    private const val DURATION_TOLERANCE_FRACTION = 0.03

    fun check(
        mode: ConversionMode,
        inputBytes: Long,
        outputBytes: Long,
        inputDurationMs: Long?,
        outputDurationMs: Long?
    ): Verdict {
        if (inputBytes <= 0) return Verdict(false, Reason.INPUT_UNREADABLE, null)
        if (outputBytes <= 0) return Verdict(false, Reason.OUTPUT_MISSING_OR_EMPTY, 0.0)
        val ratio = outputBytes.toDouble() / inputBytes
        val range = if (mode == ConversionMode.COPY) COPY_RANGE else TRANSCODE_RANGE
        if (outputBytes < MIN_OUTPUT_BYTES || ratio < range.start) return Verdict(false, Reason.OUTPUT_TOO_SMALL, ratio)
        if (ratio > range.endInclusive) return Verdict(false, Reason.OUTPUT_TOO_LARGE, ratio)
        if (inputDurationMs != null && outputDurationMs != null && inputDurationMs > 0) {
            val allowed = maxOf(DURATION_TOLERANCE_MIN_MS, (inputDurationMs * DURATION_TOLERANCE_FRACTION).toLong())
            if (kotlin.math.abs(outputDurationMs - inputDurationMs) > allowed) {
                return Verdict(false, Reason.DURATION_MISMATCH, ratio)
            }
        }
        return Verdict(true, Reason.OK, ratio)
    }
}
