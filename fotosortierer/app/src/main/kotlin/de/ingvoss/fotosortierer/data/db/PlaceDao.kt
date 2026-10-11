package de.ingvoss.fotosortierer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM place")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM place")
    suspend fun getAll(): List<PlaceEntity>

    @Query("SELECT * FROM place WHERE clusterId = :clusterId")
    suspend fun byId(clusterId: Long): PlaceEntity?

    @Query("SELECT * FROM place WHERE nameState = 0 ORDER BY clusterId LIMIT :limit")
    suspend fun pending(limit: Int): List<PlaceEntity>

    @Query("SELECT COUNT(*) FROM place WHERE nameState = 0")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(place: PlaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(places: List<PlaceEntity>)

    @Query("UPDATE place SET name = :name, nameState = :state WHERE clusterId = :clusterId")
    suspend fun setName(clusterId: Long, name: String?, state: Int)

    @Query("UPDATE place SET latitude = :lat, longitude = :lon WHERE clusterId = :clusterId")
    suspend fun setPosition(clusterId: Long, lat: Double, lon: Double)

    @Query("DELETE FROM place")
    suspend fun deleteAll()

    /** Entfernt Orte, denen kein Medium mehr zugeordnet ist. */
    @Query("DELETE FROM place WHERE clusterId NOT IN (SELECT DISTINCT clusterId FROM media_state WHERE clusterId IS NOT NULL)")
    suspend fun deleteEmpty()

    @Query("SELECT * FROM geocode_cache WHERE cellKey = :cellKey")
    suspend fun cached(cellKey: String): GeocodeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCache(entry: GeocodeCacheEntity)

    @Query("DELETE FROM geocode_cache")
    suspend fun clearCache()
}
