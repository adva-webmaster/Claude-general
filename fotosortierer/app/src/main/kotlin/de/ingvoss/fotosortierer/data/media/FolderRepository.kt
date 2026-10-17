package de.ingvoss.fotosortierer.data.media

import de.ingvoss.fotosortierer.core.DirectoryNode
import de.ingvoss.fotosortierer.core.DirectoryTree
import de.ingvoss.fotosortierer.core.FolderEntry
import de.ingvoss.fotosortierer.core.FolderOrder
import de.ingvoss.fotosortierer.data.db.AppDatabase
import de.ingvoss.fotosortierer.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Ordnerliste: [visible] (Favoriten zuerst) und die per Einstellung [hidden] ausgeblendeten Ordner. */
data class FolderOverview(
    val visible: List<FolderEntry> = emptyList(),
    val hidden: List<FolderEntry> = emptyList()
) {
    val favorites: List<FolderEntry> get() = visible.filter { it.favorite }
}

/**
 * Ordner sind die Alben (Bucket-Namen) des MediaStore. Favoriten und ausgeblendete Ordner liegen in den
 * Einstellungen; Ausblenden benutzt denselben Mechanismus wie "auszuschließende Alben" (Spalte `hidden`).
 */
class FolderRepository(db: AppDatabase, private val settings: SettingsRepository) {
    private val dao = db.mediaDao()

    val overview: Flow<FolderOverview> = combine(dao.observeFolders(), dao.observeAlbums(), settings.settings) { visible, all, s ->
        val (shown, gone) = FolderOrder.build(
            visible = visible.associate { it.groupKey to it.toCounts() },
            all = all.associate { it.groupKey to it.toCounts() },
            favorites = s.favoriteAlbums,
            hidden = s.excludedAlbums
        )
        FolderOverview(shown, gone)
    }

    /** Verzeichnisbaum aus den relativen Pfaden aller sichtbaren Medien (Zähler je Knoten inkl. Unterverzeichnisse). */
    val directories: Flow<List<DirectoryNode>> = dao.observeDirectories().map { rows ->
        DirectoryTree.build(rows.associate { it.groupKey to it.toCounts() })
    }

    suspend fun setFavorite(name: String, favorite: Boolean) = settings.setAlbumFavorite(name, favorite)

    suspend fun setHidden(name: String, hidden: Boolean) = settings.setAlbumHidden(name, hidden)
}
