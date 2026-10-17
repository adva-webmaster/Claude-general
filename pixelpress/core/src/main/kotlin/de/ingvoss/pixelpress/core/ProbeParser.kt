package de.ingvoss.pixelpress.core

/** Liest die Ausgabe von `ffprobe -of flat` (Zeilen wie `streams.stream.0.codec_name="h264"`). */
object ProbeParser {
    private val streamLine = Regex("""^streams\.stream\.(\d+)\.(codec_name|codec_type)="([^"]*)"$""")
    private val durationLine = Regex("""^format\.duration="([^"]*)"$""")

    fun parse(output: String): StreamInfo {
        val names = HashMap<Int, String>()
        val types = HashMap<Int, String>()
        var durationMs: Long? = null
        for (raw in output.lineSequence()) {
            val line = raw.trim()
            val stream = streamLine.matchEntire(line)
            if (stream != null) {
                val index = stream.groupValues[1].toInt()
                if (stream.groupValues[2] == "codec_name") names[index] = stream.groupValues[3] else types[index] = stream.groupValues[3]
                continue
            }
            durationLine.matchEntire(line)?.let { match ->
                durationMs = match.groupValues[1].toDoubleOrNull()?.takeIf { it >= 0 }?.let { Math.round(it * 1000) }
            }
        }
        fun firstOf(type: String): String? = types.entries.filter { it.value == type }.minByOrNull { it.key }?.let { names[it.key] }
        return StreamInfo(firstOf("video"), firstOf("audio"), durationMs)
    }
}
