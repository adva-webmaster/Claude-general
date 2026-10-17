package de.ingvoss.pixelpress.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PathMapperTest {
    private val primary = "/storage/emulated/0"

    @Test
    fun primarerSpeicher() {
        assertEquals("/storage/emulated/0/Movies/AVI", PathMapper.treeDocumentIdToPath("primary:Movies/AVI", primary))
        assertEquals("/storage/emulated/0", PathMapper.treeDocumentIdToPath("primary:", primary))
    }

    @Test
    fun sdKarte() {
        assertEquals("/storage/1234-ABCD/Videos", PathMapper.treeDocumentIdToPath("1234-ABCD:Videos", primary))
        assertEquals("/storage/1234-ABCD", PathMapper.treeDocumentIdToPath("1234-ABCD:", primary))
    }

    @Test
    fun dokumenteUndRohpfad() {
        assertEquals("/storage/emulated/0/Documents/Berichte", PathMapper.treeDocumentIdToPath("home:Berichte", primary))
        assertEquals("/storage/emulated/0/Documents", PathMapper.treeDocumentIdToPath("home:", primary))
        assertEquals("/storage/emulated/0/Download/x", PathMapper.treeDocumentIdToPath("raw:/storage/emulated/0/Download/x", primary))
    }

    @Test
    fun ungueltigesWirdAbgelehnt() {
        assertNull(PathMapper.treeDocumentIdToPath("", primary))
        assertNull(PathMapper.treeDocumentIdToPath("kaputt", primary))
        assertNull(PathMapper.treeDocumentIdToPath("primary:../../etc", primary))
        assertNull(PathMapper.treeDocumentIdToPath("raw:relativ/pfad", primary))
        assertNull(PathMapper.treeDocumentIdToPath("sd karte:x", primary))
    }

    @Test
    fun doppelteSchraegstricheUndPunkteWerdenBereinigt() {
        assertEquals("/storage/emulated/0/a/b", PathMapper.treeDocumentIdToPath("primary:a//./b/", primary))
    }
}
