package de.ingvoss.fotosortierer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoStackTest {
    @Test
    fun lifoReihenfolge() {
        val s = UndoStack<Int>()
        s.push(1); s.push(2); s.push(3)
        assertEquals(3, s.peek())
        assertEquals(3, s.pop())
        assertEquals(2, s.pop())
        assertEquals(1, s.pop())
        assertNull(s.pop())
        assertTrue(s.isEmpty)
    }

    @Test
    fun standardgroesseIstFuenfzig() {
        assertEquals(50, UndoStack<Int>().maxSize)
    }

    @Test
    fun aeltesteEintraegeFallenBeiUeberlaufWeg() {
        val s = UndoStack<Int>(maxSize = 50)
        for (i in 1..120) s.push(i)
        assertEquals(50, s.size)
        assertEquals((71..120).toList(), s.toList())
        // Die letzten 50 Aktionen lassen sich vollständig rückgängig machen
        for (i in 120 downTo 71) assertEquals(i, s.pop())
        assertNull(s.pop())
    }

    @Test
    fun clearLeertDenStapel() {
        val s = UndoStack<String>(2)
        s.push("a"); s.push("b"); s.push("c")
        assertEquals(listOf("b", "c"), s.toList())
        s.clear()
        assertEquals(0, s.size)
        assertNull(s.peek())
    }

    @Test(expected = IllegalArgumentException::class)
    fun nichtPositiveGroesseIstVerboten() {
        UndoStack<Int>(0)
    }
}
