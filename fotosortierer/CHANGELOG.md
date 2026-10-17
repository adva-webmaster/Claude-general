# Versionshinweise PixelSweeper

Neueste Version zuerst. Dieselben Texte stehen in der App unter *Einstellungen → Versionshinweise*
(`app/src/main/res/values/release_notes.xml`, Liste in `ui/about/AboutScreens.kt`). Bei jeder neuen Version beides ergänzen.

## Version 1.5.1 – 17.10.2026

- Neue Seite „Lizenzen und Hinweise“: VLC-Abspieler (LGPL 2.1) mit Quellen, weitere verwendete Bausteine und die Lizenztexte
- Neue Seite „Versionshinweise“ mit dem Verlauf aller Versionen

## Version 1.5.0 – 17.10.2026

- Laufbalken in Videos: Tippen oder Ziehen springt an die gewünschte Stelle, mit Zeitangaben
- AVI-Videos und andere Formate, die Android nicht darstellt, spielen jetzt mit Bild: ein eingebauter VLC-Abspieler springt automatisch ein
- Statt einer leeren Fläche zeigt die App ein Symbol, wenn eine Vorschau nicht lädt
- Die App ist durch den VLC-Abspieler größer und läuft nur auf 64-Bit-Handys

## Version 1.4.0 – 17.10.2026

- Sicherung mit Auswahlseite: Entscheidungen, gelöschte Medien, Standorte mit Ortsnamen, Einstellungen und Ordner lassen sich beim Export und beim Import einzeln wählen
- Gelöschte Medien werden mitgesichert und stellen „Gelöscht“ und „Freigegebener Speicher“ wieder her
- „Standorte werden gelesen“ bleibt nicht mehr hängen: Wenige unlesbare Dateien gelten als ohne Standort

## Version 1.3.1 – 17.10.2026

- Feste Signatur: Neue Versionen lassen sich ab jetzt direkt über die installierte installieren (bei einem Konflikt einmalig vorher deinstallieren – Sicherung exportieren und danach importieren)

## Version 1.3.0 – 17.10.2026

- Verzeichnisbaum im Reiter „Ordner“: Ein beliebiges Verzeichnis samt allen Unterverzeichnissen zum Sortieren wählen, mit Suche
- Die App heißt überall PixelSweeper (Texte, Sicherungsdatei, Download-Datei)
- Die Datenbank speichert jetzt das Verzeichnis jedes Mediums; vorhandene Daten bleiben erhalten

## Version 1.2.0 – 17.10.2026

- Taste „Heute“ im Kalender; der heutige Tag und der aktuelle Monat sind markiert
- Die Zahlen unter den Tagen im Kalender sind nicht mehr abgeschnitten
- Auf anderem Weg gelöschte Fotos und Videos verschwinden beim Start aus der App und ihrer Datenbank
- „In anderer App öffnen“ stürzt bei fehlenden Dateien nicht mehr ab

## Version 1.1.0 – 13.10.2026

- Neuer Reiter „Ordner“: Ordner lange antippen für Favorit oder Ausblenden; Favoriten als Schnellwahl auf der Startseite
- Die Einstellungen erreichst du über das Zahnrad auf der Startseite
- Neuer Name: PixelSweeper

## Version 1.0.0 – 11.10.2026

- Erste Version: Sortieren per Wischen (behalten, Papierkorb, zurückstellen) mit Rückgängig, Zoom und Videowiedergabe
- Kalender (Jahr, Monat, Tag) und Orte mit OpenStreetMap-Karte, beide miteinander verknüpft
- Korrektur-Raster, App-Papierkorb mit Löschbestätigung durch Android, erneutes Sortieren bereits entschiedener Medien
- Sicherung und Wiederherstellung, Einstellungen, Orte aus GPS-Daten mit Ortsnamen
- Design in Dunkelrot und Anthrazit; die Kopfzeile der Karte bleibt ruhig; die Sortier-Tasten reagieren bei jedem Foto

