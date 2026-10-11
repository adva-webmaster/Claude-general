package de.ingvoss.fotosortierer.core

/**
 * Begrenzter Rückgängig-Stapel. Überschreitet er [maxSize], fällt der älteste Eintrag weg.
 * Nicht threadsicher – wird nur vom Main-Thread (ViewModel) benutzt.
 */
class UndoStack<T>(val maxSize: Int = DEFAULT_MAX_SIZE) {
    private val items = ArrayDeque<T>()

    init {
        require(maxSize > 0) { "maxSize muss positiv sein" }
    }

    val size: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()

    fun push(item: T) {
        items.addLast(item)
        while (items.size > maxSize) items.removeFirst()
    }

    /** Entfernt und liefert den jüngsten Eintrag, oder null bei leerem Stapel. */
    fun pop(): T? = items.removeLastOrNull()

    fun peek(): T? = items.lastOrNull()

    fun clear() = items.clear()

    /** Alle Einträge, ältester zuerst (für Persistenz/Anzeige). */
    fun toList(): List<T> = items.toList()

    companion object {
        const val DEFAULT_MAX_SIZE = 50
    }
}
