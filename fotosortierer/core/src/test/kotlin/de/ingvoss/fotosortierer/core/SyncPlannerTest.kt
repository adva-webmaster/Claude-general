package de.ingvoss.fotosortierer.core

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlannerTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val prim = "external_primary"
    private val sd = "1234-ABCD"

    private fun time(iso: String) =
        CaptureTime.resolve(java.time.Instant.parse(iso).toEpochMilli(), null, null, zone)

    private fun scanned(
        id: Long, iso: String = "2026-03-12T10:00:00Z", volume: String = prim,
        album: String = "Camera", size: Long = 1000
    ) = ScannedMedia(volume, id, MediaKind.FOTO, "IMG_$id.jpg", album, size, 0, time(iso))

    private var nextUid = 1L
    private fun known(
        s: ScannedMedia, status: MediaStatus = MediaStatus.UNBEARBEITET
    ) = KnownMedia(
        nextUid++, s.volume, s.mediaStoreId, status, s.kind, s.displayName, s.album, s.sizeBytes,
        s.durationMs, s.time.utcMillis, s.time.localDate, s.time.localMonth
    )

    /** Wendet einen Plan auf eine In-Memory-"Datenbank" an (Spiegel der Logik der App). */
    private fun apply(db: List<KnownMedia>, plan: SyncPlan): List<KnownMedia> {
        val result = db.associateBy { it.uid }.toMutableMap()
        for (t in plan.tombstones) result[t.uid] = result.getValue(t.uid).copy(status = MediaStatus.GELOESCHT)
        for (u in plan.updates) {
            val s = u.scanned
            result[u.uid] = result.getValue(u.uid).copy(
                kind = s.kind, displayName = s.displayName, album = s.album, sizeBytes = s.sizeBytes,
                durationMs = s.durationMs, takenAtUtc = s.time.utcMillis, localDate = s.time.localDate,
                localMonth = s.time.localMonth
            )
        }
        for (u in plan.revivals) {
            val s = u.scanned
            result[u.uid] = result.getValue(u.uid).copy(
                status = MediaStatus.UNBEARBEITET, album = s.album, takenAtUtc = s.time.utcMillis,
                localDate = s.time.localDate, localMonth = s.time.localMonth
            )
        }
        var maxUid = result.keys.maxOrNull() ?: 0L
        for (s in plan.inserts) {
            maxUid++
            result[maxUid] = KnownMedia(
                maxUid, s.volume, s.mediaStoreId, MediaStatus.UNBEARBEITET, s.kind, s.displayName, s.album,
                s.sizeBytes, s.durationMs, s.time.utcMillis, s.time.localDate, s.time.localMonth
            )
        }
        return result.values.toList()
    }

    private fun dayState(db: List<KnownMedia>, day: String) =
        StatusCounts.of(db.filter { it.localDate == day }.map { it.status }).state

    @Test
    fun neueMedienWerdenAufgenommen() {
        val plan = SyncPlanner.plan(emptyList(), listOf(scanned(1), scanned(2)), setOf(prim))
        assertEquals(2, plan.inserts.size)
        assertTrue(plan.updates.isEmpty() && plan.tombstones.isEmpty() && plan.revivals.isEmpty())
    }

    @Test
    fun unveraenderteMedienErzeugenKeinenPlan() {
        val s = scanned(1)
        val plan = SyncPlanner.plan(listOf(known(s)), listOf(s), setOf(prim))
        assertTrue(plan.isEmpty)
    }

    @Test
    fun verschwundeneMedienWerdenTombstone() {
        val a = scanned(1)
        val b = scanned(2)
        val plan = SyncPlanner.plan(listOf(known(a), known(b)), listOf(a), setOf(prim))
        assertEquals(listOf(2L), plan.tombstones.map { it.mediaStoreId })
    }

    @Test
    fun bereitsGeloeschteWerdenNichtErneutTombstone() {
        val a = scanned(1)
        val b = scanned(2)
        val plan = SyncPlanner.plan(
            listOf(known(a), known(b, MediaStatus.GELOESCHT)), listOf(a), setOf(prim)
        )
        assertTrue(plan.tombstones.isEmpty())
    }

    @Test
    fun medienNichtVerfuegbarerVolumesBleibenErhalten() {
        val onPhone = scanned(1)
        val onSd = scanned(7, volume = sd)
        // SD-Karte ist ausgeworfen: nur "external_primary" wurde gescannt
        val plan = SyncPlanner.plan(listOf(known(onPhone), known(onSd)), listOf(onPhone), setOf(prim))
        assertTrue(plan.tombstones.isEmpty())
        // Ist die SD-Karte da und das Medium weg, wird es Tombstone
        val plan2 = SyncPlanner.plan(listOf(known(onPhone), known(onSd)), listOf(onPhone), setOf(prim, sd))
        assertEquals(listOf(7L), plan2.tombstones.map { it.mediaStoreId })
    }

    @Test
    fun gleicheIdAufVerschiedenenVolumesIstGetrennt() {
        val a = scanned(5, volume = prim)
        val b = scanned(5, volume = sd)
        val plan = SyncPlanner.plan(listOf(known(a)), listOf(a, b), setOf(prim, sd))
        assertEquals(1, plan.inserts.size)
        assertEquals(sd, plan.inserts.single().volume)
    }

    @Test
    fun leererScanLoeschtNichts() {
        val plan = SyncPlanner.plan(listOf(known(scanned(1)), known(scanned(2))), emptyList(), setOf(prim))
        assertTrue(plan.tombstones.isEmpty())
    }

    @Test
    fun geaenderteMetadatenErzeugenUpdateOhneStatusAenderung() {
        val old = scanned(1, album = "Camera")
        val moved = scanned(1, album = "WhatsApp Images")
        val db = listOf(known(old, MediaStatus.BEHALTEN))
        val plan = SyncPlanner.plan(db, listOf(moved), setOf(prim))
        assertEquals(1, plan.updates.size)
        assertTrue(plan.revivals.isEmpty())
        val after = apply(db, plan)
        assertEquals(MediaStatus.BEHALTEN, after.single().status)
        assertEquals("WhatsApp Images", after.single().album)
    }

    @Test
    fun geaendertesDatumErzeugtUpdate() {
        val old = scanned(1, iso = "2026-03-12T10:00:00Z")
        val redated = scanned(1, iso = "2026-03-13T10:00:00Z")
        val plan = SyncPlanner.plan(listOf(known(old)), listOf(redated), setOf(prim))
        assertEquals(1, plan.updates.size)
    }

    @Test
    fun wiederaufgetauchteTombstonesWerdenWiederbelebt() {
        val a = scanned(1)
        val db = listOf(known(a, MediaStatus.GELOESCHT))
        val plan = SyncPlanner.plan(db, listOf(a), setOf(prim))
        assertEquals(1, plan.revivals.size)
        assertTrue(plan.updates.isEmpty())
        assertEquals(MediaStatus.UNBEARBEITET, apply(db, plan).single().status)
    }

    @Test
    fun neuesMediumInErledigtemTagSetztTagAufTeilweise() {
        val a = scanned(1, iso = "2026-03-12T10:00:00Z")
        val b = scanned(2, iso = "2026-03-12T11:00:00Z")
        var db = listOf(known(a, MediaStatus.BEHALTEN), known(b, MediaStatus.PAPIERKORB))
        assertEquals(ProgressState.VOLLSTAENDIG, dayState(db, "2026-03-12"))

        val neu = scanned(3, iso = "2026-03-12T12:00:00Z")
        db = apply(db, SyncPlanner.plan(db, listOf(a, b, neu), setOf(prim)))

        assertEquals(3, db.size)
        assertEquals(ProgressState.TEILWEISE, dayState(db, "2026-03-12"))
    }

    @Test
    fun neuesMediumInNeuemTagMachtNurDiesenTagUnbearbeitet() {
        val a = scanned(1, iso = "2026-03-12T10:00:00Z")
        var db = listOf(known(a, MediaStatus.BEHALTEN))
        val neu = scanned(2, iso = "2026-03-14T10:00:00Z")
        db = apply(db, SyncPlanner.plan(db, listOf(a, neu), setOf(prim)))
        assertEquals(ProgressState.VOLLSTAENDIG, dayState(db, "2026-03-12"))
        assertEquals(ProgressState.UNBEARBEITET, dayState(db, "2026-03-14"))
    }

    @Test
    fun externGeloeschtesLetztesOffenesMediumSchliesstDenTag() {
        val a = scanned(1, iso = "2026-03-12T10:00:00Z")
        val b = scanned(2, iso = "2026-03-12T11:00:00Z")
        var db = listOf(known(a, MediaStatus.BEHALTEN), known(b, MediaStatus.UNBEARBEITET))
        assertEquals(ProgressState.TEILWEISE, dayState(db, "2026-03-12"))
        db = apply(db, SyncPlanner.plan(db, listOf(a), setOf(prim)))
        assertEquals(ProgressState.VOLLSTAENDIG, dayState(db, "2026-03-12"))
    }

    @Test
    fun alleMedienEinesTagesGeloeschtZaehltAlsVollstaendig() {
        val a = scanned(1)
        val b = scanned(2)
        var db = listOf(known(a), known(b))
        db = apply(db, SyncPlanner.plan(db, listOf(scanned(99, iso = "2026-05-01T10:00:00Z")), setOf(prim)))
        assertEquals(ProgressState.VOLLSTAENDIG, dayState(db, "2026-03-12"))
    }
}
