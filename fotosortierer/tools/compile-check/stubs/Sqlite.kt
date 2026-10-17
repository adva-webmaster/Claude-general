@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.sqlite.db

interface SupportSQLiteQuery

class SimpleSQLiteQuery(query: String, bindArgs: Array<out Any?>?) : SupportSQLiteQuery

interface SupportSQLiteDatabase {
    fun execSQL(sql: String)
}
