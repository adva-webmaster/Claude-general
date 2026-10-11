package de.ingvoss.fotosortierer.data.media

import android.content.ContentResolver
import android.content.IntentSender
import android.provider.MediaStore
import androidx.room.withTransaction
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.core.MediaSql
import de.ingvoss.fotosortierer.core.MediaStatus
import de.ingvoss.fotosortierer.data.db.AppDatabase
import de.ingvoss.fotosortierer.data.db.MediaStateEntity
import de.ingvoss.fotosortierer.data.db.toRoom

/** Ein Block von höchstens [TrashRepository.BLOCK_SIZE] Medien desselben Volumes, der in einer Systemabfrage gelöscht wird. */
data class DeleteBlock(val uids: List<Long>)

/**
 * App-interner Papierkorb. Medien im Papierkorb (Status PAPIERKORB) sind noch NICHT gelöscht. Endgültig
 * entfernt wird nur über `MediaStore.createTrashRequest`: Android zeigt dazu einen Bestätigungsdialog;
 * erst nach dessen Bestätigung markiert [markDeleted] den Block als GELOESCHT.
 */
class TrashRepository(private val db: AppDatabase, private val resolver: ContentResolver) {
    private val dao = db.mediaDao()

    /** Teilt die Medien (nach Volume getrennt) in Blöcke à [BLOCK_SIZE]. */
    suspend fun blocksFor(uids: List<Long>): List<DeleteBlock> {
        val entities = ArrayList<MediaStateEntity>(uids.size)
        for (chunk in uids.chunked(QUERY_CHUNK)) entities += dao.byUids(chunk)
        return entities
            .filter { it.mediaStatus == MediaStatus.PAPIERKORB }
            .groupBy { it.volume }
            .values
            .flatMap { group -> group.map { it.uid }.chunked(BLOCK_SIZE).map { DeleteBlock(it) } }
    }

    /** Alle Medien im Papierkorb als Blöcke. */
    suspend fun allBlocks(): List<DeleteBlock> {
        val query = MediaSql.uids(MediaFilter(statuses = setOf(MediaStatus.PAPIERKORB)))
        return blocksFor(dao.queryUids(query.toRoom()))
    }

    /** Baut die Systemabfrage für einen Block; null, wenn kein Medium des Blocks mehr existiert. */
    suspend fun createRequest(block: DeleteBlock): IntentSender? {
        val uris = dao.byUids(block.uids).map { it.contentUri() }
        if (uris.isEmpty()) return null
        return MediaStore.createTrashRequest(resolver, uris, true).intentSender
    }

    /** Nach bestätigter Systemabfrage: Block als gelöscht markieren. */
    suspend fun markDeleted(block: DeleteBlock, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            for (chunk in block.uids.chunked(QUERY_CHUNK)) dao.setStatusFor(chunk, MediaStatus.GELOESCHT.code, now)
        }
    }

    /** Aus dem App-Papierkorb zurück in die Sortier-Warteschlange. */
    suspend fun restore(uids: List<Long>, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            for (chunk in uids.chunked(QUERY_CHUNK)) dao.setStatusFor(chunk, MediaStatus.UNBEARBEITET.code, now)
        }
    }

    companion object {
        const val BLOCK_SIZE = 500
        private const val QUERY_CHUNK = 500
    }
}
