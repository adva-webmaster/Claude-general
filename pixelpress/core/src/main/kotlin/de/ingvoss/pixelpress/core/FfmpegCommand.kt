package de.ingvoss.pixelpress.core

/** Baut die Aufrufe von ffmpeg und ffprobe. */
object FfmpegCommand {
    private val copyableVideo = setOf("h264", "hevc")
    private val copyableAudio = setOf("aac", "mp3", "ac3", "eac3")

    /** Passen Bild- und Tonspur schon in eine MP4 (H.264/H.265 mit AAC/MP3/AC-3)? Dann genügt Umpacken. */
    fun canCopy(info: StreamInfo): Boolean {
        val video = info.videoCodec ?: return false
        if (video !in copyableVideo) return false
        val audio = info.audioCodec ?: return true
        return audio in copyableAudio
    }

    /** ffprobe-Aufruf: Codecs der Spuren und Dauer, im "flat"-Format (siehe [ProbeParser]). */
    fun probeArgs(file: String): List<String> = listOf(
        "-v", "error",
        "-show_entries", "stream=codec_type,codec_name:format=duration",
        "-of", "flat",
        file
    )

    /**
     * ffmpeg-Aufruf: erste Bildspur und alle Tonspuren in eine MP4 (`-f mp4`, damit auch eine Zieldatei wie "x.mp4.part"
     * geschrieben werden kann). Untertitel- und Datenspuren (z. B. Zeitcode in MOV) entfallen. Der Fortschritt kommt
     * zeilenweise auf stdout (`-progress pipe:1`).
     */
    fun convertArgs(input: String, output: String, mode: ConversionMode, quality: Quality, info: StreamInfo): List<String> {
        val args = mutableListOf(
            "-hide_banner", "-nostdin", "-y", "-loglevel", "warning",
            "-i", input,
            "-map", "0:v:0", "-map", "0:a?", "-sn", "-dn"
        )
        if (mode == ConversionMode.COPY) {
            args += listOf("-c", "copy")
            if (info.videoCodec == "hevc") args += listOf("-tag:v", "hvc1")
        } else {
            args += listOf(
                "-c:v", "libx264", "-preset", quality.preset, "-crf", quality.crf.toString(),
                "-pix_fmt", "yuv420p",
                // yuv420p braucht gerade Bildmaße
                "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",
                "-c:a", "aac", "-b:a", "160k"
            )
        }
        args += listOf("-movflags", "+faststart", "-f", "mp4", "-progress", "pipe:1", "-nostats", output)
        return args
    }
}
