package de.ingvoss.fotosortierer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryTreeTest {
    private fun counts(open: Int = 0, done: Int = 0) = StatusCounts(unbearbeitet = open, behalten = done)

    private val tree = DirectoryTree.build(
        mapOf(
            "Pictures/Urlaub/2024/Strand/" to counts(open = 3),
            "Pictures/Urlaub/" to counts(open = 1, done = 1),
            "Pictures/Urlaub/2023/" to counts(done = 4),
            "DCIM/Camera/" to counts(open = 10),
            "Download/" to counts(done = 2),
            "pictures/Zimmer/" to counts(open = 1)
        )
    )

    @Test
    fun wurzelnSindAlphabetischOhneGrossKleinschreibung() {
        assertEquals(listOf("DCIM", "Download", "Pictures", "pictures"), tree.map { it.name })
    }

    @Test
    fun zaehlerUmfassenUnterverzeichnisse() {
        val pictures = tree.first { it.path == "Pictures/" }
        assertEquals(0, pictures.counts.let { it.total - (3 + 1 + 1 + 4) })
        val urlaub = pictures.children.single()
        assertEquals("Pictures/Urlaub/", urlaub.path)
        assertEquals(StatusCounts(unbearbeitet = 4, behalten = 5), urlaub.counts)
        assertEquals(listOf("2023", "2024"), urlaub.children.map { it.name })
    }

    @Test
    fun zwischenverzeichnisseOhneEigeneMedienEntstehenVonSelbst() {
        val y2024 = tree.first { it.path == "Pictures/" }.children.single().children.first { it.name == "2024" }
        assertEquals(StatusCounts(unbearbeitet = 3), y2024.counts)
        assertEquals("Pictures/Urlaub/2024/Strand/", y2024.children.single().path)
    }

    @Test
    fun aufgeklappteVerzeichnisseZeigenIhreKinder() {
        val closed = DirectoryTree.flatten(tree, emptySet())
        assertEquals(listOf("DCIM", "Download", "Pictures", "pictures"), closed.map { it.node.name })
        assertTrue(closed.none { it.expanded })

        val open = DirectoryTree.flatten(tree, setOf("Pictures/", "Pictures/Urlaub/", "Download/"))
        assertEquals(
            listOf("DCIM", "Download", "Pictures", "Urlaub", "2023", "2024", "pictures"),
            open.map { it.node.name }
        )
        assertEquals(listOf(0, 0, 0, 1, 2, 2, 0), open.map { it.depth })
        // Download hat keine Kinder und gilt deshalb nie als aufgeklappt
        assertEquals(listOf(false, false, true, true, false, false, false), open.map { it.expanded })
    }

    @Test
    fun sucheFindetAufJederEbeneUndIgnoriertGrossKleinschreibung() {
        val hits = DirectoryTree.search(tree, "strand")
        assertEquals(listOf("Pictures/Urlaub/2024/Strand/"), hits.map { it.path })
        val urlaub = DirectoryTree.search(tree, "URLAUB").map { it.path }
        assertEquals(
            listOf("Pictures/Urlaub/", "Pictures/Urlaub/2023/", "Pictures/Urlaub/2024/", "Pictures/Urlaub/2024/Strand/"),
            urlaub
        )
        assertTrue(DirectoryTree.search(tree, "  ").isEmpty())
    }

    @Test
    fun leereUndUngueltigePfadeWerdenIgnoriert() {
        assertTrue(DirectoryTree.build(mapOf("" to counts(open = 1), "/" to counts(open = 1))).isEmpty())
        val t = DirectoryTree.build(mapOf("/Pictures//Urlaub" to counts(open = 2)))
        assertEquals("Pictures/Urlaub/", t.single().children.single().path)
    }
}
