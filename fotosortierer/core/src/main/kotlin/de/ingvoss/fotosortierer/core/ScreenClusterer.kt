package de.ingvoss.fotosortierer.core

import kotlin.math.floor
import kotlin.math.hypot

/** Ein Punkt in Bildschirmkoordinaten (Pixel). */
data class ScreenItem(val id: Long, val x: Float, val y: Float)

/** Ergebnis: Mitglieder und Mittelpunkt (Mittelwert der Mitglieder). */
data class ScreenCluster(val ids: List<Long>, val x: Float, val y: Float)

/**
 * Fasst Kartenmarker, die auf dem Bildschirm näher als [radiusPx] beieinander liegen, zusammen. Wird beim
 * Zeichnen der Karte pro Frame aufgerufen (wenige tausend Orte) und arbeitet deshalb mit einem Raster.
 */
object ScreenClusterer {
    private class Group(val leaderX: Float, val leaderY: Float) {
        val ids = ArrayList<Long>(2)
        var sumX = 0f
        var sumY = 0f
    }

    fun cluster(items: List<ScreenItem>, radiusPx: Float): List<ScreenCluster> {
        require(radiusPx > 0f) { "Radius muss positiv sein" }
        val groups = ArrayList<Group>()
        val grid = HashMap<Long, MutableList<Group>>()

        fun cell(v: Float): Int = floor(v / radiusPx).toInt()
        fun cellKey(cx: Int, cy: Int): Long = (cx.toLong() shl 32) xor (cy.toLong() and 0xffffffffL)

        for (item in items) {
            val cx = cell(item.x)
            val cy = cell(item.y)
            var best: Group? = null
            var bestDistance = Float.MAX_VALUE
            for (gx in cx - 1..cx + 1) {
                for (gy in cy - 1..cy + 1) {
                    val candidates = grid[cellKey(gx, gy)] ?: continue
                    for (g in candidates) {
                        val d = hypot(item.x - g.leaderX, item.y - g.leaderY)
                        if (d <= radiusPx && d < bestDistance) {
                            best = g
                            bestDistance = d
                        }
                    }
                }
            }
            val target = best ?: Group(item.x, item.y).also {
                groups.add(it)
                grid.getOrPut(cellKey(cx, cy)) { ArrayList(1) }.add(it)
            }
            target.ids.add(item.id)
            target.sumX += item.x
            target.sumY += item.y
        }
        return groups.map { ScreenCluster(it.ids, it.sumX / it.ids.size, it.sumY / it.ids.size) }
    }
}
