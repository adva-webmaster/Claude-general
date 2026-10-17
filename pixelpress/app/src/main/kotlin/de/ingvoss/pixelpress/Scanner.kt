package de.ingvoss.pixelpress

import de.ingvoss.pixelpress.core.ConversionPlanner
import de.ingvoss.pixelpress.core.SourceFile
import java.io.File
import java.nio.file.Files

/** Durchsucht ein Verzeichnis nach AVI/MOV-Dateien und vorhandenen MP4-Dateien. */
object Scanner {
    class Result(val sources: List<SourceFile>, val existingMp4: Set<String>)

    fun scan(root: File, recursive: Boolean): Result {
        val sources = ArrayList<SourceFile>()
        val existing = HashSet<String>()
        val pending = ArrayDeque<File>()
        pending.addLast(root)
        while (pending.isNotEmpty()) {
            val directory = pending.removeLast()
            val children = directory.listFiles() ?: continue
            for (child in children) {
                if (child.name.startsWith(".")) continue // versteckt (z. B. .thumbnails, .trashed)
                if (child.isDirectory) {
                    if (recursive && !Files.isSymbolicLink(child.toPath())) pending.addLast(child)
                } else if (ConversionPlanner.isSource(child.name)) {
                    sources += SourceFile(child.path, child.length())
                } else if (ConversionPlanner.extensionOf(child.name) == "mp4") {
                    existing += child.path
                }
            }
        }
        return Result(sources, existing)
    }
}
