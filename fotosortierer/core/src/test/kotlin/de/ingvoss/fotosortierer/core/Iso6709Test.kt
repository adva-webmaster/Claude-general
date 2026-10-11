package de.ingvoss.fotosortierer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Iso6709Test {
    @Test
    fun parstTypischeVideoMetadaten() {
        assertEquals(LatLon(54.3233, 10.1228), Iso6709.parse("+54.3233+010.1228/"))
        assertEquals(LatLon(54.3233, 10.1228), Iso6709.parse("+54.3233+010.1228+012.000/"))
        assertEquals(LatLon(-33.8688, 151.2093), Iso6709.parse("-33.8688+151.2093/"))
        assertEquals(LatLon(40.0, -74.0), Iso6709.parse(" +40-074/ "))
    }

    @Test
    fun ungueltigesLiefertNull() {
        assertNull(Iso6709.parse(null))
        assertNull(Iso6709.parse(""))
        assertNull(Iso6709.parse("irgendwas"))
        assertNull(Iso6709.parse("+95.0+010.0/"))   // Breite außerhalb
        assertNull(Iso6709.parse("+00.0000+000.0000/")) // 0/0 = "kein GPS"
    }
}
