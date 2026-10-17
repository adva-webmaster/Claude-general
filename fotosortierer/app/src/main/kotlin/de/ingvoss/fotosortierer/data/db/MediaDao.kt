package de.ingvoss.fotosortierer.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import de.ingvoss.fotosortierer.core.MediaSql
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    // ---- Aggregationen (SQL GROUP BY, siehe MediaSql) -------------------------------------------

    @Query(MediaSql.Q_NO_PLACE)
    fun observeNoPlace(): Flow<GroupCountsRow>

    @Query(MediaSql.Q_OVERALL)
    fun observeOverall(): Flow<GroupCountsRow>

    @Query(MediaSql.Q_ALBUMS)
    fun observeAlbums(): Flow<List<GroupCountsRow>>

    /** Ordner mit Zählern der sichtbaren Medien (ausgeblendete Ordner haben keine sichtbaren Medien). */
    @Query(MediaSql.Q_FOLDERS)
    fun observeFolders(): Flow<List<GroupCountsRow>>

    /** Verzeichnisse (relativer Pfad) mit Zählern der direkt darin liegenden sichtbaren Medien. */
    @Query(MediaSql.Q_DIRECTORIES)
    fun observeDirectories(): Flow<List<GroupCountsRow>>

    @Query(MediaSql.Q_TRASH_BYTES)
    fun observeTrashBytes(): Flow<Long>

    @Query(MediaSql.Q_FREED_BYTES)
    fun observeFreedBytes(): Flow<Long>

    @Query(MediaSql.Q_OLDEST_UNPROCESSED_TIME)
    fun observeOldestOpenTime(): Flow<Long?>

    // ---- Dynamische Abfragen (MediaFilter → SQL) ------------------------------------------------

    @RawQuery(observedEntities = [MediaStateEntity::class])
    fun pagedList(query: SupportSQLiteQuery): PagingSource<Int, MediaStateEntity>

    @RawQuery(observedEntities = [MediaStateEntity::class])
    fun observeCounts(query: SupportSQLiteQuery): Flow<GroupCountsRow>

    /** Gruppierungen (Monate/Tage) innerhalb eines Filters, siehe `MediaSql.groupedMonths` und `groupedDays`. */
    @RawQuery(observedEntities = [MediaStateEntity::class])
    fun observeGroups(query: SupportSQLiteQuery): Flow<List<GroupCountsRow>>

    /** Orte innerhalb eines Filters, siehe `MediaSql.groupedPlaces`. */
    @RawQuery(observedEntities = [MediaStateEntity::class])
    fun observePlaceGroups(query: SupportSQLiteQuery): Flow<List<PlaceCountsRow>>

    @RawQuery(observedEntities = [MediaStateEntity::class])
    fun observeInt(query: SupportSQLiteQuery): Flow<Int>

    @RawQuery(observedEntities = [MediaStateEntity::class])
    fun observeSessionSummary(query: SupportSQLiteQuery): Flow<SessionSummaryRow>

    @RawQuery(observedEntities = [MediaStateEntity::class])
    suspend fun queueItems(query: SupportSQLiteQuery): List<MediaStateEntity>

    @RawQuery(observedEntities = [MediaStateEntity::class])
    suspend fun queryUids(query: SupportSQLiteQuery): List<Long>

    @RawQuery(observedEntities = [MediaStateEntity::class])
    suspend fun queryInt(query: SupportSQLiteQuery): Int

    // ---- Einzelzugriffe ---------------------------------------------------------------------

    @Query("SELECT * FROM media_state WHERE uid = :uid")
    suspend fun byUid(uid: Long): MediaStateEntity?

    @Query("SELECT * FROM media_state WHERE uid IN (:uids)")
    suspend fun byUids(uids: List<Long>): List<MediaStateEntity>

    @Query("UPDATE media_state SET status = :status, statusChangedAt = :at WHERE uid IN (:uids)")
    suspend fun setStatusFor(uids: List<Long>, status: Int, at: Long)

    /** Entfernt Medien endgültig aus der Datenbank (auf anderem Weg gelöschte Dateien). */
    @Query("DELETE FROM media_state WHERE uid IN (:uids)")
    suspend fun deleteByUids(uids: List<Long>)

    @Query("UPDATE media_state SET status = :status, statusChangedAt = :at WHERE uid = :uid")
    suspend fun setStatus(uid: Long, status: Int, at: Long)

    /** Alle Medien mit Entscheidung (Status ≠ UNBEARBEITET), z. B. für die Sicherung. */
    @Query("SELECT * FROM media_state WHERE status != 0")
    suspend fun nonDefault(): List<MediaStateEntity>

    // ---- Sync -------------------------------------------------------------------------------

    @Query(
        "SELECT uid, volume, mediaStoreId, status, kind, displayName, album, sizeBytes, durationMs, " +
            "takenAtUtc, localDate, localMonth, relativePath FROM media_state"
    )
    suspend fun allKnown(): List<KnownRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<MediaStateEntity>): List<Long>

    @Query(
        "UPDATE media_state SET takenAtUtc = :takenAt, localDate = :localDate, localMonth = :localMonth, " +
            "album = :album, kind = :kind, sizeBytes = :size, durationMs = :duration, displayName = :name, " +
            "hidden = :hidden, relativePath = :relativePath WHERE uid = :uid"
    )
    suspend fun updateMeta(
        uid: Long,
        takenAt: Long,
        localDate: String,
        localMonth: String,
        album: String,
        kind: Int,
        size: Long,
        duration: Long,
        name: String,
        hidden: Boolean,
        relativePath: String
    )

    /** Blendet Medien aus, deren Typ oder Album per Einstellung ausgeschlossen ist. */
    @Query("UPDATE media_state SET hidden = CASE WHEN kind IN (:kinds) OR album IN (:albums) THEN 1 ELSE 0 END")
    suspend fun applyHidden(kinds: List<Int>, albums: List<String>)

    // ---- Standorte und Orte -----------------------------------------------------------------

    @Query(
        "SELECT uid, volume, mediaStoreId, kind FROM media_state " +
            "WHERE locationChecked = 0 AND status != 4 AND uid > :afterUid ORDER BY uid LIMIT :limit"
    )
    suspend fun locationJobs(afterUid: Long, limit: Int): List<LocationJobRow>

    @Query("UPDATE media_state SET latitude = :lat, longitude = :lon, locationChecked = 1, clusterId = NULL WHERE uid = :uid")
    suspend fun setLocation(uid: Long, lat: Double, lon: Double)

    @Query("UPDATE media_state SET locationChecked = 1 WHERE uid = :uid")
    suspend fun markLocationChecked(uid: Long)

    @Query(
        "SELECT COUNT(*) AS total, COALESCE(SUM(locationChecked), 0) AS checked, " +
            "COALESCE(SUM(CASE WHEN latitude IS NOT NULL THEN 1 ELSE 0 END), 0) AS withLocation " +
            "FROM media_state WHERE status != 4"
    )
    fun observeLocationProgress(): Flow<LocationProgressRow>

    @Query(
        "SELECT uid AS id, latitude AS lat, longitude AS lon FROM media_state " +
            "WHERE latitude IS NOT NULL AND clusterId IS NULL ORDER BY takenAtUtc, uid"
    )
    suspend fun unclusteredPoints(): List<GeoPointRow>

    @Query("UPDATE media_state SET clusterId = :clusterId WHERE uid IN (:uids)")
    suspend fun assignCluster(uids: List<Long>, clusterId: Long)

    @Query("UPDATE media_state SET clusterId = NULL")
    suspend fun resetClusters()

    /** Setzt alles zurück, was aus Standorten abgeleitet ist (Entscheidungen bleiben erhalten). */
    @Query("UPDATE media_state SET latitude = NULL, longitude = NULL, clusterId = NULL, locationChecked = 0")
    suspend fun resetLocations()

    @Query(
        "SELECT clusterId AS clusterId, AVG(latitude) AS lat, AVG(longitude) AS lon FROM media_state " +
            "WHERE clusterId IS NOT NULL GROUP BY clusterId"
    )
    suspend fun centroids(): List<CentroidRow>
}
