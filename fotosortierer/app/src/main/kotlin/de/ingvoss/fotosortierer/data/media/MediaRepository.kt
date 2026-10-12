package de.ingvoss.fotosortierer.data.media

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.core.MediaSql
import de.ingvoss.fotosortierer.core.MediaStatus
import de.ingvoss.fotosortierer.core.QueuePhase
import de.ingvoss.fotosortierer.core.StatusCounts
import de.ingvoss.fotosortierer.data.db.AppDatabase
import de.ingvoss.fotosortierer.data.db.GroupCountsRow
import de.ingvoss.fotosortierer.data.db.MediaStateEntity
import de.ingvoss.fotosortierer.data.db.PlaceCountsRow
import de.ingvoss.fotosortierer.data.db.SessionSummaryRow
import de.ingvoss.fotosortierer.data.db.toRoom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Merkt einen früheren Zustand eines Mediums, um eine Änderung rückgängig zu machen. */
data class StatusSnapshot(val uid: Long, val status: MediaStatus, val changedAt: Long)

/** Lese- und Schreibzugriff auf die Medien-Tabelle, ausgedrückt in [MediaFilter]n. */
class MediaRepository(private val db: AppDatabase) {
    private val dao = db.mediaDao()

    fun pagedMedia(filter: MediaFilter, oldestFirst: Boolean): Flow<PagingData<MediaStateEntity>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = PAGE_SIZE, enablePlaceholders = true)) {
            dao.pagedList(MediaSql.list(filter, oldestFirst).toRoom())
        }.flow

    fun counts(filter: MediaFilter): Flow<StatusCounts> =
        dao.observeCounts(MediaSql.counts(filter).toRoom()).map { it.toCounts() }

    /** Monate der Medien in [scope] (z. B. nur die eines Ortes). */
    fun months(scope: MediaFilter): Flow<List<GroupCountsRow>> =
        dao.observeGroups(MediaSql.groupedMonths(scope).toRoom())

    /** Tage des Monats, den [scopeWithMonth] angibt. */
    fun days(scopeWithMonth: MediaFilter): Flow<List<GroupCountsRow>> =
        dao.observeGroups(MediaSql.groupedDays(scopeWithMonth).toRoom())

    /** Orte der Medien in [scope] (z. B. nur die eines Zeitraums). */
    fun places(scope: MediaFilter): Flow<List<PlaceCountsRow>> =
        dao.observePlaceGroups(MediaSql.groupedPlaces(scope).toRoom())

    /** Anzahl der noch offenen Medien einer Warteschlange als Strom. */
    fun queueCountFlow(filter: MediaFilter, phase: QueuePhase, phase2StartedAt: Long): Flow<Int> =
        dao.observeInt(MediaSql.queueCount(filter, phase, phase2StartedAt).toRoom())

    fun sessionSummary(filter: MediaFilter, startedAt: Long): Flow<SessionSummaryRow> =
        dao.observeSessionSummary(MediaSql.sessionSummary(filter, startedAt).toRoom())

    suspend fun queue(
        filter: MediaFilter,
        phase: QueuePhase,
        phase2StartedAt: Long,
        oldestFirst: Boolean,
        limit: Int
    ): List<MediaStateEntity> =
        dao.queueItems(MediaSql.queue(filter, phase, phase2StartedAt, oldestFirst, limit).toRoom())

    suspend fun queueCount(filter: MediaFilter, phase: QueuePhase, phase2StartedAt: Long): Int =
        dao.queryInt(MediaSql.queueCount(filter, phase, phase2StartedAt).toRoom())

    /** Anzahl der Medien im App-Papierkorb. */
    suspend fun trashCount(): Int =
        dao.queryInt(MediaSql.count(MediaFilter(statuses = setOf(MediaStatus.PAPIERKORB))).toRoom())

    suspend fun uids(filter: MediaFilter): List<Long> = dao.queryUids(MediaSql.uids(filter).toRoom())

    suspend fun byUid(uid: Long): MediaStateEntity? = dao.byUid(uid)

    /** Setzt den Status eines Mediums und liefert den früheren Zustand für "Rückgängig". */
    suspend fun setStatus(uid: Long, status: MediaStatus, now: Long = System.currentTimeMillis()): StatusSnapshot? {
        val before = dao.byUid(uid) ?: return null
        dao.setStatus(uid, status.code, now)
        return StatusSnapshot(uid, before.mediaStatus, before.statusChangedAt)
    }

    /** Setzt den Status mehrerer Medien (Mehrfachauswahl); liefert die früheren Zustände. */
    suspend fun setStatusBulk(uids: List<Long>, status: MediaStatus, now: Long = System.currentTimeMillis()): List<StatusSnapshot> {
        val snapshots = ArrayList<StatusSnapshot>(uids.size)
        db.withTransaction {
            for (chunk in uids.chunked(CHUNK)) {
                dao.byUids(chunk).mapTo(snapshots) { StatusSnapshot(it.uid, it.mediaStatus, it.statusChangedAt) }
                dao.setStatusFor(chunk, status.code, now)
            }
        }
        return snapshots
    }

    /** Stellt frühere Zustände wieder her. */
    suspend fun restore(snapshots: List<StatusSnapshot>) {
        db.withTransaction {
            for (s in snapshots) dao.setStatus(s.uid, s.status.code, s.changedAt)
        }
    }

    private companion object {
        const val PAGE_SIZE = 60
        const val CHUNK = 500
    }
}
