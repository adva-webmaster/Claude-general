package de.ingvoss.pixelpress

import de.ingvoss.pixelpress.core.ConversionMode
import de.ingvoss.pixelpress.core.FfmpegCommand
import de.ingvoss.pixelpress.core.ProbeParser
import de.ingvoss.pixelpress.core.ProgressParser
import de.ingvoss.pixelpress.core.Quality
import de.ingvoss.pixelpress.core.StreamInfo
import java.io.IOException

sealed interface ConvertResult {
    data object Success : ConvertResult
    data class Failed(val message: String) : ConvertResult
}

class ProbeResult(val info: StreamInfo, val error: String?)

/** Aufrufe von ffprobe (Codecs, Dauer) und ffmpeg (Umwandeln mit Fortschritt). */
class FfmpegTool(private val runtime: FfmpegRuntime) {

    /** Codecs und Dauer einer Datei. Scheitert ffprobe, steht der Grund in [ProbeResult.error]. */
    suspend fun probe(path: String): ProbeResult {
        val text = StringBuilder()
        val errors = ArrayDeque<String>()
        return try {
            val code = runtime.run(runtime.ffprobe, FfmpegCommand.probeArgs(path), { text.appendLine(it) }) { line ->
                synchronized(errors) {
                    errors.addLast(line)
                    if (errors.size > 3) errors.removeFirst()
                }
            }
            if (code == 0) {
                ProbeResult(ProbeParser.parse(text.toString()), null)
            } else {
                val reason = synchronized(errors) { errors.joinToString(" ") }.takeLast(240)
                ProbeResult(StreamInfo(null, null, null), "Code $code: $reason")
            }
        } catch (e: IOException) {
            ProbeResult(StreamInfo(null, null, null), e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Wandelt [input] nach [output] um. [onProgress] bekommt Anteil (0..1, null = unbekannt) und Geschwindigkeit (z. B. 2,5-fach).
     */
    suspend fun convert(
        input: String,
        output: String,
        mode: ConversionMode,
        quality: Quality,
        info: StreamInfo,
        onProgress: (Float?, Double?) -> Unit
    ): ConvertResult {
        val progress = ProgressParser()
        val tail = ArrayDeque<String>()
        val args = FfmpegCommand.convertArgs(input, output, mode, quality, info)
        return try {
            val code = runtime.run(
                runtime.ffmpeg, args,
                onOut = { line ->
                    progress.feed(line)
                    if (line.startsWith("progress=")) onProgress(progress.fraction(info.durationMs), progress.speed)
                },
                onErr = { line ->
                    synchronized(tail) {
                        tail.addLast(line)
                        if (tail.size > TAIL_LINES) tail.removeFirst()
                    }
                }
            )
            if (code == 0) {
                ConvertResult.Success
            } else {
                val message = synchronized(tail) { tail.joinToString("\n") }.takeLast(MAX_MESSAGE)
                ConvertResult.Failed(message.ifBlank { "ffmpeg: Code $code" })
            }
        } catch (e: IOException) {
            ConvertResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private companion object {
        const val TAIL_LINES = 6
        const val MAX_MESSAGE = 300
    }
}
