package de.ingvoss.fotosortierer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeTest {
    private val schwelle = 200f

    @Test
    fun dominanteAchseEntscheidet() {
        assertEquals(SwipeDirection.LINKS, SwipeGeometry.directionOf(-120f, 40f))
        assertEquals(SwipeDirection.RECHTS, SwipeGeometry.directionOf(120f, -100f))
        assertEquals(SwipeDirection.OBEN, SwipeGeometry.directionOf(30f, -90f))
        assertEquals(SwipeDirection.KEINE, SwipeGeometry.directionOf(30f, 90f)) // nach unten wird nicht gewischt
    }

    @Test
    fun gleichstandGehtAnDieHorizontale() {
        assertEquals(SwipeDirection.RECHTS, SwipeGeometry.directionOf(50f, -50f))
        assertEquals(SwipeDirection.LINKS, SwipeGeometry.directionOf(-50f, -50f))
    }

    @Test
    fun totzoneLiefertKeine() {
        assertEquals(SwipeDirection.KEINE, SwipeGeometry.directionOf(5f, -4f, deadZonePx = 10f))
        assertEquals(SwipeDirection.RECHTS, SwipeGeometry.directionOf(11f, -4f, deadZonePx = 10f))
    }

    @Test
    fun fortschrittWachstBisZurSchwelleUndWirdBegrenzt() {
        assertEquals(0f, SwipeGeometry.progress(0f, 0f, schwelle), 0f)
        assertEquals(0.5f, SwipeGeometry.progress(100f, 10f, schwelle), 1e-6f)
        assertEquals(0.5f, SwipeGeometry.progress(-100f, 10f, schwelle), 1e-6f)
        assertEquals(0.25f, SwipeGeometry.progress(10f, -50f, schwelle), 1e-6f)
        assertEquals(1f, SwipeGeometry.progress(900f, 0f, schwelle), 0f)
        assertEquals(0f, SwipeGeometry.progress(10f, 90f, schwelle), 0f) // nach unten
    }

    @Test
    fun beiLosslassenVorDerSchwelleSpringtDieKarteZurueck() {
        assertEquals(SwipeDirection.KEINE, SwipeGeometry.resolve(150f, 0f, 0f, 0f, schwelle))
        assertEquals(SwipeDirection.KEINE, SwipeGeometry.resolve(-150f, 20f, -300f, 0f, schwelle))
        assertEquals(SwipeDirection.KEINE, SwipeGeometry.resolve(0f, -150f, 0f, 0f, schwelle))
    }

    @Test
    fun beiErreichenDerSchwelleWirdAusgeloest() {
        assertEquals(SwipeDirection.RECHTS, SwipeGeometry.resolve(200f, 10f, 0f, 0f, schwelle))
        assertEquals(SwipeDirection.LINKS, SwipeGeometry.resolve(-260f, -30f, 0f, 0f, schwelle))
        assertEquals(SwipeDirection.OBEN, SwipeGeometry.resolve(20f, -210f, 0f, 0f, schwelle))
    }

    @Test
    fun schnelleWischgesteLoestVorDerSchwelleAus() {
        assertEquals(SwipeDirection.RECHTS, SwipeGeometry.resolve(80f, 0f, 2000f, 0f, schwelle))
        assertEquals(SwipeDirection.LINKS, SwipeGeometry.resolve(-80f, 0f, -2000f, 0f, schwelle))
        assertEquals(SwipeDirection.OBEN, SwipeGeometry.resolve(0f, -80f, 0f, -2000f, schwelle))
    }

    @Test
    fun schnelleGesteInGegenrichtungOderZuKurzLoestNichtAus() {
        // Geschwindigkeit zeigt nach links, Verschiebung nach rechts
        assertEquals(SwipeDirection.KEINE, SwipeGeometry.resolve(80f, 0f, -2000f, 0f, schwelle))
        // Strecke unter einem Viertel der Schwelle
        assertEquals(SwipeDirection.KEINE, SwipeGeometry.resolve(30f, 0f, 3000f, 0f, schwelle))
    }

    @Test
    fun standardbelegung() {
        val m = SwipeMapping.STANDARD
        assertEquals(SwipeAction.BEHALTEN, m.actionFor(SwipeDirection.LINKS))
        assertEquals(SwipeAction.PAPIERKORB, m.actionFor(SwipeDirection.RECHTS))
        assertEquals(SwipeAction.ZURUECKSTELLEN, m.actionFor(SwipeDirection.OBEN))
        assertEquals(null, m.actionFor(SwipeDirection.KEINE))
        assertEquals(SwipeDirection.RECHTS, m.directionOf(SwipeAction.PAPIERKORB))
        assertEquals(MediaStatus.ZURUECKGESTELLT, SwipeAction.ZURUECKSTELLEN.status)
    }

    @Test
    fun zuweisungTauschtMitDerBisherigenRichtung() {
        val m = SwipeMapping.STANDARD.with(SwipeDirection.LINKS, SwipeAction.PAPIERKORB)
        assertEquals(SwipeMapping(SwipeAction.PAPIERKORB, SwipeAction.BEHALTEN, SwipeAction.ZURUECKSTELLEN), m)
        val n = SwipeMapping.STANDARD.with(SwipeDirection.OBEN, SwipeAction.BEHALTEN)
        assertEquals(SwipeMapping(SwipeAction.ZURUECKSTELLEN, SwipeAction.PAPIERKORB, SwipeAction.BEHALTEN), n)
    }

    @Test
    fun gleicheZuweisungAendertNichts() {
        assertEquals(SwipeMapping.STANDARD, SwipeMapping.STANDARD.with(SwipeDirection.RECHTS, SwipeAction.PAPIERKORB))
        assertEquals(SwipeMapping.STANDARD, SwipeMapping.STANDARD.with(SwipeDirection.KEINE, SwipeAction.PAPIERKORB))
    }

    @Test
    fun belegungBleibtBeiJederZuweisungGueltig() {
        for (start in listOf(SwipeMapping.STANDARD, SwipeMapping(SwipeAction.PAPIERKORB, SwipeAction.ZURUECKSTELLEN, SwipeAction.BEHALTEN))) {
            for (dir in listOf(SwipeDirection.LINKS, SwipeDirection.RECHTS, SwipeDirection.OBEN)) {
                for (action in SwipeAction.entries) {
                    val m = start.with(dir, action)
                    assertEquals(action, m.actionFor(dir))
                    assertEquals(3, setOf(m.links, m.rechts, m.oben).size)
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun doppelteAktionIstVerboten() {
        SwipeMapping(SwipeAction.BEHALTEN, SwipeAction.BEHALTEN, SwipeAction.PAPIERKORB)
    }

    @Test
    fun gespeicherteNamenWerdenGeprueft() {
        assertEquals(
            SwipeMapping(SwipeAction.PAPIERKORB, SwipeAction.BEHALTEN, SwipeAction.ZURUECKSTELLEN),
            SwipeMapping.fromNames("PAPIERKORB", "BEHALTEN", "ZURUECKSTELLEN")
        )
        assertEquals(SwipeMapping.STANDARD, SwipeMapping.fromNames("PAPIERKORB", "PAPIERKORB", "BEHALTEN"))
        assertEquals(SwipeMapping.STANDARD, SwipeMapping.fromNames(null, "x", ""))
        assertTrue(SwipeMapping.fromNames("BEHALTEN", "PAPIERKORB", "ZURUECKSTELLEN") == SwipeMapping.STANDARD)
    }
}
