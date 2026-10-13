package de.ingvoss.fotosortierer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderOrderTest {
    private val open = StatusCounts(unbearbeitet = 3, behalten = 1)
    private val done = StatusCounts(behalten = 4)

    private fun entry(name: String, counts: StatusCounts = open, favorite: Boolean = false) =
        FolderEntry(name, counts, favorite)

    @Test
    fun favoritenZuerstDannOffeneDannErledigte() {
        val sorted = FolderOrder.sort(
            listOf(
                entry("Alpha", done),
                entry("Zebra"),
                entry("Beta", favorite = true),
                entry("Camera"),
                entry("Omega", done, favorite = true)
            )
        )
        assertEquals(listOf("Beta", "Omega", "Camera", "Zebra", "Alpha"), sorted.map { it.name })
    }

    @Test
    fun alphabetischOhneGrossKleinschreibungUndStabil() {
        val sorted = FolderOrder.sort(listOf(entry("zeta"), entry("Alpha"), entry("beta"), entry("Beta")))
        assertEquals(listOf("Alpha", "Beta", "beta", "zeta"), sorted.map { it.name })
    }

    @Test
    fun leereZaehlerGeltenAlsErledigt() {
        val sorted = FolderOrder.sort(listOf(entry("Leer", StatusCounts.EMPTY), entry("Offen")))
        assertEquals(listOf("Offen", "Leer"), sorted.map { it.name })
    }

    @Test
    fun zurueckgestelltZaehltAlsOffen() {
        val sorted = FolderOrder.sort(
            listOf(entry("A", done), entry("B", StatusCounts(behalten = 1, zurueckgestellt = 1)))
        )
        assertEquals(listOf("B", "A"), sorted.map { it.name })
    }

    @Test
    fun suchePasstTeilstueckeOhneGrossKleinschreibung() {
        val all = listOf(entry("WhatsApp Images"), entry("Camera"), entry("Screenshots"))
        assertEquals(listOf("WhatsApp Images"), FolderOrder.filter(all, " whats ").map { it.name })
        assertEquals(all, FolderOrder.filter(all, ""))
        assertEquals(all, FolderOrder.filter(all, "   "))
        assertTrue(FolderOrder.filter(all, "xyz").isEmpty())
    }

    @Test
    fun listeTrenntSichtbareUndAusgeblendeteUndStreichtFavoritenAusgeblendeter() {
        val visible = mapOf("Camera" to open, "Download" to done, "Alt" to open)
        val all = visible + mapOf("Memes" to open, "Alt" to open)
        val (shown, gone) = FolderOrder.build(
            visible = visible,
            all = all,
            favorites = setOf("Download", "Memes"),
            hidden = setOf("Alt", "Memes")
        )
        assertEquals(listOf("Download", "Camera"), shown.map { it.name })
        assertEquals(listOf(true, false), shown.map { it.favorite })
        assertEquals(listOf("Alt", "Memes"), gone.map { it.name })
        assertTrue(gone.all { it.hidden && !it.favorite })
    }

    @Test
    fun ausgeblendeterOrdnerOhneMedienErscheintNichtInDerListe() {
        val (shown, gone) = FolderOrder.build(emptyMap(), emptyMap(), emptySet(), setOf("Verschwunden"))
        assertTrue(shown.isEmpty())
        assertTrue(gone.isEmpty())
    }
}
