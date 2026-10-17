package de.ingvoss.fotosortierer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterCodecTest {
    private fun roundTrip(f: MediaFilter) = FilterCodec.decode(FilterCodec.encode(f))

    @Test
    fun leererFilterHatPlatzhalter() {
        assertEquals(FilterCodec.EMPTY, FilterCodec.encode(MediaFilter.ALL))
        assertEquals(MediaFilter.ALL, FilterCodec.decode(FilterCodec.EMPTY))
        assertEquals(MediaFilter.ALL, FilterCodec.decode(null))
        assertEquals(MediaFilter.ALL, FilterCodec.decode(""))
    }

    @Test
    fun alleFelderUeberlebenDenRundlauf() {
        val f = MediaFilter(
            localDate = "2026-03-12",
            localMonth = "2026-03",
            clusterId = 42,
            withoutPlace = true,
            album = "Urlaub; Sommer = schön & \"toll\" 100%",
            kind = MediaKind.VIDEO,
            statuses = setOf(MediaStatus.ZURUECKGESTELLT, MediaStatus.UNBEARBEITET)
        )
        assertEquals(f, roundTrip(f))
    }

    @Test
    fun verzeichnisMitSonderzeichenUeberlebtDenRundlauf() {
        val f = MediaFilter(pathPrefix = "Pictures/Urlaub 2024/Köln; Dom = schön & 100%/", statuses = setOf(MediaStatus.BEHALTEN))
        assertEquals(f, roundTrip(f))
        assertTrue(FilterCodec.encode(f).all { it.isLetterOrDigit() || it == '_' || it == '-' })
    }

    @Test
    fun einzelneFelder() {
        for (f in listOf(
            MediaFilter(localDate = "2026-01-01"),
            MediaFilter(localMonth = "2025-12"),
            MediaFilter(clusterId = 7),
            MediaFilter(withoutPlace = true),
            MediaFilter(album = "Camera"),
            MediaFilter(kind = MediaKind.SCREENSHOT),
            MediaFilter(statuses = emptySet()),
            MediaFilter(statuses = setOf(MediaStatus.PAPIERKORB))
        )) assertEquals(f, roundTrip(f))
    }

    @Test
    fun tokenIstUrlSicher() {
        val token = FilterCodec.encode(MediaFilter(album = "Ünïcödé / Pfad?x=1&y=2#frag", localDate = "2026-03-12"))
        assertTrue(token, token.matches(Regex("[A-Za-z0-9_-]+")))
    }

    @Test
    fun kaputteTokensLiefernAllFilter() {
        assertEquals(MediaFilter.ALL, FilterCodec.decode("%%%"))
        assertEquals(MediaFilter.ALL, FilterCodec.decode("!!!"))
    }

    @Test
    fun jahrUndMehrereOrteUeberlebenDenRundlauf() {
        val f = MediaFilter(localYear = "2026", clusterIds = setOf(5, 3, 9))
        assertEquals(f, roundTrip(f))
        assertEquals(MediaFilter(clusterIds = emptySet()), roundTrip(MediaFilter(clusterIds = emptySet())))
    }

    @Test
    fun zeitUndOrtsbereichLassenSichTrennen() {
        val f = MediaFilter(localMonth = "2026-03", clusterIds = setOf(1, 2), statuses = setOf(MediaStatus.BEHALTEN))
        assertEquals(MediaFilter(localMonth = "2026-03"), f.timeScope())
        assertEquals(MediaFilter(clusterIds = setOf(1, 2)), f.placeScope())
        assertTrue(f.hasTimeScope && f.hasPlaceScope)
        assertEquals(false, MediaFilter(album = "x").hasPlaceScope)
        assertEquals(true, MediaFilter(withoutPlace = true).hasPlaceScope)
    }
}
