package de.ingvoss.fotosortierer.data.db

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import de.ingvoss.fotosortierer.core.SqlQuery

/** Übersetzt die von :core gebauten SQL-Abfragen in Room-Abfragen. */
fun SqlQuery.toRoom(): SupportSQLiteQuery = SimpleSQLiteQuery(sql, args.toTypedArray())
