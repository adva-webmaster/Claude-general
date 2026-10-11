package de.ingvoss.fotosortierer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MediaStateEntity::class, PlaceEntity::class, GeocodeCacheEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun placeDao(): PlaceDao

    companion object {
        const val FILE_NAME = "fotosortierer.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME).build()
    }
}
