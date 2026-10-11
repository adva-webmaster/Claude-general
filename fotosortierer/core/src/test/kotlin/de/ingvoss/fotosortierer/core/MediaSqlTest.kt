package de.ingvoss.fotosortierer.core

import java.sql.Connection
import java.sql.DriverManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Führt die SQL-Bausteine gegen eine echte SQLite-Datenbank aus (sqlite-jdbc). */
class MediaSqlTest {
    private lateinit var db: Connection

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().use { it.execute(MediaTable.CREATE_SQL) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private data class Row(
        val id: Long,
        val date: String,
        val status: MediaStatus = MediaStatus.UNBEARBEITET,
        val takenAt: Long = 0,
        val cluster: Long? = null,
        val lat: Double? = null,
        val album: String = "Camera",
        val kind: MediaKind = MediaKind.FOTO,
        val size: Long = 100,
        val changedAt: Long = 0,
        val hidden: Boolean = false
    )

    private fun insert(r: Row) {
        db.prepareStatement(
            "INSERT INTO media_state (mediaStoreId, volume, takenAtUtc, localDate, localMonth, latitude, " +
                "longitude, clusterId, album, kind, sizeBytes, status, statusChangedAt, displayName, durationMs, " +
                "locationChecked, hidden) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { st ->
            st.setLong(1, r.id)
            st.setString(2, "external_primary")
            st.setLong(3, r.takenAt)
            st.setString(4, r.date)
            st.setString(5, r.date.substring(0, 7))
            if (r.lat == null) st.setNull(6, java.sql.Types.REAL) else st.setDouble(6, r.lat)
            if (r.lat == null) st.setNull(7, java.sql.Types.REAL) else st.setDouble(7, 10.0)
            if (r.cluster == null) st.setNull(8, java.sql.Types.INTEGER) else st.setLong(8, r.cluster)
            st.setString(9, r.album)
            st.setInt(10, r.kind.code)
            st.setLong(11, r.size)
            st.setInt(12, r.status.code)
            st.setLong(13, r.changedAt)
            st.setString(14, "IMG_${r.id}.jpg")
            st.setLong(15, 0)
            st.setInt(16, 1)
            st.setInt(17, if (r.hidden) 1 else 0)
            st.executeUpdate()
        }
    }

    private fun query(q: SqlQuery): List<Map<String, Any?>> = query(q.sql, q.args)

    /** Ersetzt Room-Namensparameter (:name) in Reihenfolge ihres Auftretens durch '?'. */
    private fun query(sql: String, args: List<Any?>): List<Map<String, Any?>> {
        db.prepareStatement(sql.replace(Regex(":[a-zA-Z_]+"), "?")).use { st ->
            args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
            st.executeQuery().use { rs ->
                val md = rs.metaData
                val out = ArrayList<Map<String, Any?>>()
                while (rs.next()) {
                    out.add((1..md.columnCount).associate { md.getColumnLabel(it) to rs.getObject(it) })
                }
                return out
            }
        }
    }

    private fun Map<String, Any?>.counts() = StatusCounts(
        unbearbeitet = (getValue("unbearbeitet") as Number).toInt(),
        behalten = (getValue("behalten") as Number).toInt(),
        papierkorb = (getValue("papierkorb") as Number).toInt(),
        zurueckgestellt = (getValue("zurueckgestellt") as Number).toInt(),
        geloescht = (getValue("geloescht") as Number).toInt()
    )

    private fun seed() {
        // 12. März: komplett bearbeitet; 13. März: teilweise; 14. März: unbearbeitet; April: nur zurückgestellt
        insert(Row(1, "2026-03-12", MediaStatus.BEHALTEN, takenAt = 100, cluster = 1, lat = 54.0))
        insert(Row(2, "2026-03-12", MediaStatus.PAPIERKORB, takenAt = 110, cluster = 1, lat = 54.0, size = 500))
        insert(Row(3, "2026-03-13", MediaStatus.BEHALTEN, takenAt = 200, cluster = 2, lat = 53.0))
        insert(Row(4, "2026-03-13", MediaStatus.UNBEARBEITET, takenAt = 210, cluster = 2, lat = 53.0))
        insert(Row(5, "2026-03-14", MediaStatus.UNBEARBEITET, takenAt = 300))
        insert(Row(6, "2026-04-01", MediaStatus.ZURUECKGESTELLT, takenAt = 400, changedAt = 50))
        insert(Row(7, "2026-04-01", MediaStatus.GELOESCHT, takenAt = 410, size = 900))
        insert(Row(8, "2026-03-12", MediaStatus.UNBEARBEITET, takenAt = 120, hidden = true)) // ausgeblendet
    }

    @Test
    fun monatsaggregationLiefertZaehlerJeMonat() {
        seed()
        val rows = query(MediaSql.Q_MONTHS, emptyList())
        assertEquals(listOf("2026-03", "2026-04"), rows.map { it["groupKey"] })
        val maerz = rows[0].counts()
        assertEquals(StatusCounts(unbearbeitet = 2, behalten = 2, papierkorb = 1), maerz) // id 8 ist hidden
        assertEquals(ProgressState.TEILWEISE, maerz.state)
        val april = rows[1].counts()
        assertEquals(StatusCounts(zurueckgestellt = 1, geloescht = 1), april)
        assertEquals(ProgressState.TEILWEISE, april.state) // zurückgestelltes offen, gelöschtes bearbeitet
    }

    @Test
    fun tagesaggregationEinesMonats() {
        seed()
        val rows = query(MediaSql.Q_DAYS_OF_MONTH, listOf("2026-03"))
        assertEquals(listOf("2026-03-12", "2026-03-13", "2026-03-14"), rows.map { it["groupKey"] })
        assertEquals(ProgressState.VOLLSTAENDIG, rows[0].counts().state)
        assertEquals(ProgressState.TEILWEISE, rows[1].counts().state)
        assertEquals(ProgressState.UNBEARBEITET, rows[2].counts().state)
    }

    @Test
    fun ortsaggregationMitZeitraum() {
        seed()
        val rows = query(MediaSql.Q_PLACES, emptyList())
        assertEquals(2, rows.size)
        val ort1 = rows.single { (it["clusterId"] as Number).toLong() == 1L }
        assertEquals(100L, (ort1["firstAt"] as Number).toLong())
        assertEquals(110L, (ort1["lastAt"] as Number).toLong())
        assertEquals(ProgressState.VOLLSTAENDIG, ort1.counts().state)
        val ort2 = rows.single { (it["clusterId"] as Number).toLong() == 2L }
        assertEquals(ProgressState.TEILWEISE, ort2.counts().state)
    }

    @Test
    fun ohneOrtZaehltMedienOhneGps() {
        seed()
        val row = query(MediaSql.Q_NO_PLACE, emptyList()).single()
        // ohne GPS und sichtbar: id 5, 6, 7
        assertEquals(3, (row["total"] as Number).toInt())
        assertEquals(StatusCounts(unbearbeitet = 1, zurueckgestellt = 1, geloescht = 1), row.counts())
    }

    @Test
    fun leereTabelleLiefertNullZaehlerStattNull() {
        val overall = query(MediaSql.Q_OVERALL, emptyList()).single()
        assertEquals(StatusCounts.EMPTY, overall.counts())
        assertEquals(ProgressState.LEER, overall.counts().state)
        val noPlace = query(MediaSql.Q_NO_PLACE, emptyList()).single()
        assertEquals(StatusCounts.EMPTY, noPlace.counts())
        assertEquals(0L, (query(MediaSql.Q_TRASH_BYTES, emptyList()).single().values.single() as Number).toLong())
    }

    @Test
    fun gesamtUndSpeichersummen() {
        seed()
        val overall = query(MediaSql.Q_OVERALL, emptyList()).single().counts()
        assertEquals(7, overall.total)
        assertEquals(500L, (query(MediaSql.Q_TRASH_BYTES, emptyList()).single().values.single() as Number).toLong())
        assertEquals(900L, (query(MediaSql.Q_FREED_BYTES, emptyList()).single().values.single() as Number).toLong())
    }

    @Test
    fun aeltestesUnbearbeitetesMedium() {
        seed()
        val t = query(MediaSql.Q_OLDEST_UNPROCESSED_TIME, emptyList()).single().values.single() as Number
        assertEquals(210L, t.toLong()) // id 4 (id 8 ist ausgeblendet)
    }

    @Test
    fun albenListeEnthaeltAuchAusgeblendeteAberKeineTombstones() {
        seed()
        insert(Row(9, "2026-03-20", album = "Screenshots", hidden = true))
        val rows = query(MediaSql.Q_ALBUMS, emptyList())
        assertEquals(listOf("Camera", "Screenshots"), rows.map { it["groupKey"] })
        assertEquals(7, (rows[0]["total"] as Number).toInt()) // 1-6 + versteckte id 8, ohne Tombstone id 7
    }

    @Test
    fun filterKombinationenUndSortierung() {
        seed()
        val tag = query(MediaSql.list(MediaFilter(localDate = "2026-03-12")))
        assertEquals(listOf(1L, 2L), tag.map { (it["mediaStoreId"] as Number).toLong() }) // 8 ist hidden
        val absteigend = query(MediaSql.list(MediaFilter(localMonth = "2026-03"), ascending = false))
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), absteigend.map { (it["mediaStoreId"] as Number).toLong() })
        val ort = query(MediaSql.list(MediaFilter(clusterId = 2)))
        assertEquals(listOf(3L, 4L), ort.map { (it["mediaStoreId"] as Number).toLong() })
        val ohneOrt = query(MediaSql.list(MediaFilter(withoutPlace = true)))
        assertEquals(listOf(5L, 6L, 7L), ohneOrt.map { (it["mediaStoreId"] as Number).toLong() })
        val nurVideos = query(MediaSql.list(MediaFilter(kind = MediaKind.VIDEO)))
        assertTrue(nurVideos.isEmpty())
        val status = query(MediaSql.list(MediaFilter(statuses = setOf(MediaStatus.BEHALTEN, MediaStatus.PAPIERKORB))))
        assertEquals(listOf(1L, 2L, 3L), status.map { (it["mediaStoreId"] as Number).toLong() })
        val keineStatus = query(MediaSql.list(MediaFilter(statuses = emptySet())))
        assertTrue(keineStatus.isEmpty())
    }

    @Test
    fun albumFilterIstGegenSqlInjectionGeschuetzt() {
        seed()
        insert(Row(10, "2026-03-20", album = "O'Reilly \"x\"; DROP TABLE media_state;--"))
        val rows = query(MediaSql.list(MediaFilter(album = "O'Reilly \"x\"; DROP TABLE media_state;--")))
        assertEquals(1, rows.size)
        assertEquals(8, (query(MediaSql.count(MediaFilter.ALL)).single().values.single() as Number).toInt())
    }

    @Test
    fun zaehlerEinesFilters() {
        seed()
        val c = query(MediaSql.counts(MediaFilter(localMonth = "2026-03"))).single().counts()
        assertEquals(StatusCounts(unbearbeitet = 2, behalten = 2, papierkorb = 1), c)
    }

    @Test
    fun warteschlangePhaseEinsUnbearbeitetNachZeit() {
        seed()
        insert(Row(11, "2026-03-01", MediaStatus.UNBEARBEITET, takenAt = 5))
        val q = MediaSql.queue(MediaFilter.ALL, QueuePhase.UNBEARBEITET, 0, ascending = true, limit = 3)
        assertEquals(listOf(11L, 4L, 5L), query(q).map { (it["mediaStoreId"] as Number).toLong() })
        val neueste = MediaSql.queue(MediaFilter.ALL, QueuePhase.UNBEARBEITET, 0, ascending = false, limit = 3)
        assertEquals(listOf(5L, 4L, 11L), query(neueste).map { (it["mediaStoreId"] as Number).toLong() })
        assertEquals(3, (query(MediaSql.queueCount(MediaFilter.ALL, QueuePhase.UNBEARBEITET, 0)).single().values.single() as Number).toInt())
    }

    @Test
    fun warteschlangePhaseZweiSchliesstErneutZurueckgestellteAus() {
        insert(Row(1, "2026-03-12", MediaStatus.ZURUECKGESTELLT, takenAt = 10, changedAt = 100))
        insert(Row(2, "2026-03-12", MediaStatus.ZURUECKGESTELLT, takenAt = 20, changedAt = 900)) // in Phase 2 erneut zurückgestellt
        insert(Row(3, "2026-03-12", MediaStatus.BEHALTEN, takenAt = 30))
        val q = MediaSql.queue(MediaFilter.ALL, QueuePhase.ZURUECKGESTELLT, phase2StartedAt = 500, ascending = true, limit = 5)
        assertEquals(listOf(1L), query(q).map { (it["mediaStoreId"] as Number).toLong() })
    }

    @Test
    fun warteschlangeIgnorierstatusEinschraenkungDesFilters() {
        seed()
        val filter = MediaFilter(localMonth = "2026-03", statuses = setOf(MediaStatus.BEHALTEN))
        val q = MediaSql.queue(filter, QueuePhase.UNBEARBEITET, 0, ascending = true, limit = 5)
        assertEquals(listOf(4L, 5L), query(q).map { (it["mediaStoreId"] as Number).toLong() })
    }

    @Test
    fun sitzungszusammenfassungZaehltNurEntscheidungenSeitSitzungsbeginn() {
        insert(Row(1, "2026-03-12", MediaStatus.BEHALTEN, changedAt = 1000))
        insert(Row(2, "2026-03-12", MediaStatus.PAPIERKORB, changedAt = 1100, size = 700))
        insert(Row(3, "2026-03-12", MediaStatus.ZURUECKGESTELLT, changedAt = 1200))
        insert(Row(4, "2026-03-12", MediaStatus.PAPIERKORB, changedAt = 500, size = 9999)) // frühere Sitzung
        insert(Row(5, "2026-03-12", MediaStatus.UNBEARBEITET, changedAt = 0))
        insert(Row(6, "2026-03-13", MediaStatus.BEHALTEN, changedAt = 1300))               // anderer Tag
        val row = query(MediaSql.sessionSummary(MediaFilter(localDate = "2026-03-12"), startedAt = 1000)).single()
        assertEquals(StatusCounts(behalten = 1, papierkorb = 1, zurueckgestellt = 1), row.counts())
        assertEquals(700L, (row["trashBytes"] as Number).toLong())
    }

    @Test
    fun versteckteMedienErscheinenNie() {
        insert(Row(1, "2026-03-12", hidden = true))
        assertEquals(0, (query(MediaSql.count(MediaFilter.ALL)).single().values.single() as Number).toInt())
        assertTrue(query(MediaSql.Q_MONTHS, emptyList()).isEmpty())
    }
}
