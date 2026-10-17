package de.ingvoss.fotosortierer.core

/**
 * Schutz vor Fehlalarmen bei der Dateiprüfung: Fehlt scheinbar mehr als die Hälfte aller Dateien eines Volumes,
 * ist eher der Speicher gerade nicht lesbar (Berechtigung, Einhängen) als wirklich gelöscht – dann wird nichts entfernt.
 */
object MissingFileGuard {
    fun trusted(checked: Int, missing: Int): Boolean = missing * 2 <= checked
}
