package de.ingvoss.fotosortierer.core

/**
 * Entscheidet, wann Medien, deren Position sich nicht lesen ließ, endgültig als "ohne Standort" gelten.
 * Wenige Dateien, die auch beim zweiten Versuch nicht lesbar sind, sind defekt oder haben ein unbekanntes Format –
 * sonst bliebe die Anzeige "Standorte werden gelesen: 8583 von 8597" für immer stehen. Viele Fehlschläge deuten
 * dagegen auf ein allgemeines Problem (Berechtigung, Speicher) hin; dann wird beim nächsten Durchgang neu versucht.
 */
object LocationRetryPolicy {
    /** Höchstens ein Zehntel der Versuche darf fehlschlagen, damit die Fehlschläge als dauerhaft gelten. */
    fun giveUp(failed: Int, attempted: Int): Boolean = failed > 0 && failed * 10 <= attempted
}
