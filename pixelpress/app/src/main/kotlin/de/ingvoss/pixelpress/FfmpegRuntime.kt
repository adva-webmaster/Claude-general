package de.ingvoss.pixelpress

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipFile

/**
 * Macht FFmpeg startklar und startet es. Das Paket (youtubedl-android, FFmpeg 7.1 aus dem Termux-Bau) enthält zwei Programme
 * (`libffmpeg.so`, `libffprobe.so`, werden beim Installieren in den Bibliotheksordner der App ausgepackt und können von dort
 * gestartet werden) und ein ZIP (`libffmpeg.zip.so`) mit den zugehörigen Bibliotheken, das beim ersten Start entpackt wird.
 */
class FfmpegRuntime(private val context: Context) {
    private val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    private val rootDir = File(context.noBackupFilesDir, "ffmpeg")
    private val libDir = File(rootDir, "usr/lib")

    val ffmpeg: File get() = File(nativeDir, "libffmpeg.so")
    val ffprobe: File get() = File(nativeDir, "libffprobe.so")

    /** Entpackt die Bibliotheken, falls noch nicht geschehen. Liefert null bei Erfolg, sonst einen Fehlertext. */
    suspend fun prepare(): String? = withContext(Dispatchers.IO) {
        val zip = File(nativeDir, "libffmpeg.zip.so")
        if (!zip.exists() || !ffmpeg.exists() || !ffprobe.exists()) {
            return@withContext context.getString(R.string.error_ffmpeg_missing)
        }
        val marker = File(rootDir, "ready")
        val stamp = "${zip.length()}-${zip.lastModified()}"
        if (marker.exists() && marker.readText() == stamp && libDir.isDirectory) return@withContext null
        try {
            rootDir.deleteRecursively()
            libDir.mkdirs()
            unpack(zip)
            marker.writeText(stamp)
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Entpacken fehlgeschlagen", e)
            rootDir.deleteRecursively()
            context.getString(R.string.error_ffmpeg_unpack, e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Entpackt alle Bibliotheken (`*.so*`). Verknüpfungen (z. B. `libavcodec.so.61` → `libavcodec.so.61.19.101`) liegen im ZIP
     * als kurze Textdatei mit dem Zielnamen; sie werden als echte symbolische Verknüpfungen angelegt.
     */
    private fun unpack(zipFile: File) {
        val base = rootDir.canonicalFile
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().asSequence().filter { !it.isDirectory && it.name.contains(".so") }.toList()
            val names = entries.map { it.name }.toSet()
            val links = ArrayList<Pair<File, String>>()
            for (entry in entries) {
                val target = File(rootDir, entry.name).canonicalFile
                require(target.path.startsWith(base.path + File.separator)) { "Ungültiger Pfad im Paket: ${entry.name}" }
                target.parentFile?.mkdirs()
                if (entry.size in 1..MAX_LINK_TEXT) {
                    val text = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }.trim()
                    val directory = entry.name.substringBeforeLast('/', "")
                    if (LINK_TARGET.matches(text) && "$directory/$text" in names) {
                        links += target to text
                        continue
                    }
                }
                zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
            }
            for ((link, text) in links) {
                link.delete()
                Files.createSymbolicLink(link.toPath(), Paths.get(text))
            }
        }
    }

    /**
     * Startet [binary] mit [args] und liest stdout/stderr zeilenweise. Wird die Coroutine abgebrochen, wird das Programm
     * beendet. Rückgabe: Beendigungscode.
     */
    suspend fun run(binary: File, args: List<String>, onOut: (String) -> Unit, onErr: (String) -> Unit): Int = coroutineScope {
        val builder = ProcessBuilder(listOf(binary.absolutePath) + args)
        builder.environment().apply {
            put("LD_LIBRARY_PATH", libDir.absolutePath)
            put("TMPDIR", context.cacheDir.absolutePath)
            put("HOME", context.cacheDir.absolutePath)
        }
        val process = withContext(Dispatchers.IO) { builder.start() }
        val out = async(Dispatchers.IO) { process.inputStream.bufferedReader().forEachLine(onOut) }
        val err = async(Dispatchers.IO) { process.errorStream.bufferedReader().forEachLine(onErr) }
        try {
            out.await()
            err.await()
            withContext(Dispatchers.IO) { process.waitFor() }
        } catch (e: CancellationException) {
            process.destroyForcibly()
            throw e
        }
    }

    private companion object {
        const val TAG = "FfmpegRuntime"
        const val MAX_LINK_TEXT = 200L
        val LINK_TARGET = Regex("[A-Za-z0-9._+-]+")
    }
}
