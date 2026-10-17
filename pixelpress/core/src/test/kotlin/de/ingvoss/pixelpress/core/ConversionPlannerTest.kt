package de.ingvoss.pixelpress.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionPlannerTest {
    private fun file(path: String, size: Long = 1000) = SourceFile(path, size)

    @Test
    fun nurAviUndMovGroessenunabhaengig() {
        assertTrue(ConversionPlanner.isSource("film.avi"))
        assertTrue(ConversionPlanner.isSource("FILM.MOV"))
        assertTrue(ConversionPlanner.isSource("a.b.Mov"))
        assertFalse(ConversionPlanner.isSource("film.mp4"))
        assertFalse(ConversionPlanner.isSource("avi"))
        assertFalse(ConversionPlanner.isSource("film.avi.txt"))
    }

    @Test
    fun mp4LiegtNebenDerQuelleMitGleichemNamen() {
        val jobs = ConversionPlanner.plan(listOf(file("/s/Urlaub/film.avi")), emptySet(), true)
        assertEquals("/s/Urlaub/film.mp4", jobs.single().outputPath)
        assertNull(jobs.single().skip)
    }

    @Test
    fun vorhandeneMp4WirdUebersprungenWennGewuenscht() {
        val existing = setOf("/s/a/Film.MP4")
        val skipped = ConversionPlanner.plan(listOf(file("/s/a/film.avi")), existing, skipExisting = true).single()
        assertEquals(SkipReason.OUTPUT_EXISTS, skipped.skip)
        val renamed = ConversionPlanner.plan(listOf(file("/s/a/film.avi")), existing, skipExisting = false).single()
        assertNull(renamed.skip)
        assertEquals("/s/a/film_avi.mp4", renamed.outputPath) // nie überschreiben
    }

    @Test
    fun gleicherNameMitAviUndMovBekommtZweitenNamen() {
        val jobs = ConversionPlanner.plan(listOf(file("/s/film.mov"), file("/s/film.avi")), emptySet(), true)
        // sortiert nach Pfad: film.avi zuerst
        assertEquals(listOf("/s/film.avi", "/s/film.mov"), jobs.map { it.source.path })
        assertEquals(listOf("/s/film.mp4", "/s/film_mov.mp4"), jobs.map { it.outputPath })
        assertTrue(jobs.all { it.skip == null })
    }

    @Test
    fun weitereKollisionenWerdenDurchgezaehlt() {
        val existing = setOf("/s/film.mp4", "/s/film_avi.mp4")
        val job = ConversionPlanner.plan(listOf(file("/s/film.avi")), existing, skipExisting = false).single()
        assertEquals("/s/film_avi_2.mp4", job.outputPath)
    }

    @Test
    fun ausgabenKollidierenNieMiteinander() {
        val jobs = ConversionPlanner.plan(
            listOf(file("/s/a.avi"), file("/s/a.AVI"), file("/s/a.mov"), file("/t/a.avi")), emptySet(), true
        )
        val outputs = jobs.map { it.outputPath.lowercase() }
        assertEquals(outputs.size, outputs.toSet().size)
    }

    @Test
    fun dateiOhnePunktImNamenBehaeltIhnAlsBasis() {
        val job = ConversionPlanner.plan(listOf(file("/s/ohnepunkt")), emptySet(), true).single()
        assertEquals("/s/ohnepunkt.mp4", job.outputPath)
    }
}
