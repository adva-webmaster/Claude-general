package de.ingvoss.pixelpress.core

/** Wie umgewandelt wird: nur den Container wechseln (verlustfrei, schnell) oder Bild und Ton neu berechnen. */
enum class ConversionMode { COPY, TRANSCODE }

/** Qualitätsstufe der Neuberechnung (H.264, "crf": kleiner = besser und größer). */
enum class Quality(val preset: String, val crf: Int) {
    FAST("ultrafast", 28),
    BALANCED("veryfast", 23),
    HIGH("medium", 20)
}

/** Was ffprobe über eine Datei verrät: Codec der ersten Bild- und Tonspur, Dauer. */
data class StreamInfo(val videoCodec: String?, val audioCodec: String?, val durationMs: Long?)
