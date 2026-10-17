@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.room

import android.content.Context
import kotlin.reflect.KClass

annotation class Entity(val tableName: String = "", val indices: Array<Index> = [])
annotation class Index(val value: Array<String> = [], val name: String = "", val unique: Boolean = false)
annotation class PrimaryKey(val autoGenerate: Boolean = false)
annotation class ColumnInfo(val name: String = "[field-name]", val defaultValue: String = "[value-not-set]")
annotation class Dao
annotation class Query(val value: String)
annotation class RawQuery(val observedEntities: Array<KClass<*>> = [])
annotation class Insert(val onConflict: Int = OnConflictStrategy.ABORT)
annotation class Database(val entities: Array<KClass<*>>, val version: Int, val exportSchema: Boolean = true)

object OnConflictStrategy {
    const val REPLACE = 1
    const val ABORT = 3
    const val IGNORE = 5
}

abstract class RoomDatabase {
    class Builder<T : RoomDatabase> {
        fun addMigrations(vararg migrations: androidx.room.migration.Migration): Builder<T> = this
        fun build(): T = error("stub")
    }
}

object Room {
    fun <T : RoomDatabase> databaseBuilder(context: Context, klass: Class<T>, name: String?): RoomDatabase.Builder<T> =
        RoomDatabase.Builder()
}

suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R = block()

