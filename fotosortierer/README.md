# Fotosortierer

Android-App zum schnellen Aussortieren von Fotos und Videos per Wischgeste (Vorbild: „Slidebox“).
Kotlin · Jetpack Compose · Material 3 · Room · WorkManager · osmdroid (OpenStreetMap). Oberfläche komplett
auf Deutsch. **Keine Werbung, keine Statistik, kein Tracking.**

> ## Stand der Prüfung
>
> Die App wird bei GitHub automatisch gebaut (`.github/workflows/fotosortierer-apk.yml`). Letzter geprüfter Lauf:
>
> | Prüfung | Ergebnis |
> |---|---|
> | `./gradlew assembleDebug` (Debug-APK) | ✅ erfolgreich |
> | `./gradlew test` (Unit-Tests `:core` und `:app`) | ✅ erfolgreich (129 Tests im Kern) |
> | `./gradlew lint` | ✅ 0 Fehler, 2 Hinweise (Framework-ExifInterface, `allowBackup`) |
> | Test auf einem echten Gerät, Compose-Wischtest (`connectedDebugAndroidTest`) | ❌ nicht ausgeführt |
>
> **APK herunterladen:** GitHub → Reiter **Actions** → „Fotosortierer – APK bauen“ → letzter grüner Lauf auf dem Branch
> `fotosortierer` → unten bei **Artifacts** `fotosortierer-debug-apk` (ZIP mit `app-debug.apk`). Dafür muss man bei GitHub angemeldet sein.
> Den Bau kann man dort auch selbst per „Run workflow“ neu starten.
>
> Die App wurde noch nicht auf einem Gerät ausprobiert – Fehler im Zusammenspiel (z. B. Berechtigungsdialog, Karte,
> Löschen) sind möglich. Hilfsmittel aus der Entwicklung ohne Android-SDK (`tools/`) bleiben als Zusatzprüfung erhalten.

## Funktionen

- **Sortieren per Wischen:** Karte nach links = *Behalten*, rechts = *Papierkorb*, oben = *Zurückstellen*
  (Belegung in den Einstellungen tauschbar). Dominante Achse entscheidet; unterhalb der Schwelle springt die
  Karte zurück. Farbige Rückmeldung, Symbol und Text beim Ziehen, haptisches Signal, Schaltflächen als
  Alternative, **Rückgängig** (mind. 50 Schritte). Pinch- und Doppeltipp-Zoom. Videos laufen stumm in
  Endlosschleife (Tippen = Pause, Lautsprecher-Taste = Ton, Fortschrittsbalken).
- **Reihenfolge:** zuerst Unbearbeitete (älteste zuerst, umschaltbar), danach auf Wunsch die Zurückgestellten.
  Zusammenfassung am Ende mit „Papierkorb jetzt leeren“ und „Zurückgestellte jetzt durchgehen“. Jede
  Entscheidung wird sofort gespeichert; nach einem Neustart geht es genau dort weiter.
- **Kalender** Jahr → Monat → Tag mit farbenblind-sicherer Zustandsanzeige (Farbe **und** Form: gestrichelter
  Ring = unbearbeitet, Teil-Ring = teilweise, gefüllt mit Haken = vollständig; rosa Punkt = Zurückgestellte).
- **Orte:** OpenStreetMap-Karte mit geclusterten Markern und Bottom-Sheet, Ortsliste (sortierbar nach Name,
  Anzahl, Fortschritt), Eintrag „Ohne Ort“. Orte entstehen aus GPS-Daten (Radius einstellbar, Standard 1 km);
  Namen per Android-Geocoder, ersatzweise Nominatim (höchstens 1 Anfrage/s), dauerhaft zwischengespeichert,
  sonst Koordinaten.
- **Erneut sortieren:** Bereits entschiedene Medien lassen sich per Wischen noch einmal durchgehen – in der Detailansicht
  eines Tages/Monats/Ortes („Alle erneut sortieren“ oder, nach Wahl eines Status-Filters, „Behalten erneut sortieren“ usw.),
  ebenso über Antippen einer Zeile in der Statistik auf der Startseite.
- **Karte ↔ Kalender:** Ein Tipp auf einen Marker oder Cluster öffnet die Medien aller darin liegenden Orte
  (Sortieren, Ansehen, Kalender); gezoomt wird weiter per Geste. Für ein Jahr, einen Monat oder Tag lässt sich die Karte öffnen
  (nur Orte mit Medien aus diesem Zeitraum), und von einem Ort aus der Kalender (nur Medien dieses Ortes).
- **Korrektur:** Raster mit Statusrahmen je Tag/Monat/Ort, Status ändern per Antippen oder Mehrfachauswahl
  (langer Druck), mit „Rückgängig“.
- **App-Papierkorb:** nur eine Markierung. Endgültig gelöscht wird **ausschließlich** über
  `MediaStore.createTrashRequest` nach der Systembestätigung von Android (Blöcke zu höchstens 500 Medien);
  erst danach wird ein Block als gelöscht markiert. Einmaliger Hinweis auf den 30-Tage-System-Papierkorb.
- **Start/Statistik, Einstellungen** (Wischbelegung, Reihenfolge, Ortsradius, Medientypen, Alben ausschließen,
  Hell/Dunkel/System, Haptik, Videoton, Infozeile), **JSON-Export/-Import** der Entscheidungen, **Datenbank
  neu aufbauen**.

## Voraussetzungen

- JDK 17 oder neuer (Gradle 9.6 läuft darauf; der Wrapper liegt bei).
- Android-SDK mit Plattform **android-37** (`compileSdk`/`targetSdk` 37; minSdk 30 = Android 11).
  Ist API 37 bei dir nicht verfügbar, senke `compileSdk`/`targetSdk` in `app/build.gradle.kts` (eine Stelle)
  auf 36.
- Internet für den ersten Gradle-Lauf (Google Maven + Maven Central).

## Bauen

```bash
cd fotosortierer

# SDK-Pfad einmalig festlegen (oder ANDROID_HOME setzen):
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
# Fehlende Pakete/Lizenzen per Kommandozeile installieren, falls kein Android Studio:
#   sdkmanager --licenses
#   sdkmanager "platforms;android-37" "build-tools;37.0.0" "platform-tools"

./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew test                 # Unit-Tests (:core und :app)
./gradlew lint                 # Android Lint (Fehler brechen ab, Warnungen nicht)
./gradlew connectedDebugAndroidTest   # Compose-Test der Wischgesten (Gerät/Emulator nötig)
```

Nur der reine Kotlin-Kern (ohne SDK, ohne Google Maven):

```bash
./gradlew -Dfoto.coreOnly=true :core:test
```

Die Application-ID steht zentral in `gradle.properties` (`app.applicationId`, Vorgabe
`de.ingvoss.fotosortierer`) und lässt sich beim Bauen überschreiben:

```bash
./gradlew assembleDebug -Papp.applicationId=de.beispiel.fotosortierer
```

## Auf dem Handy installieren (Sideloading)

1. Debug-APK bauen (siehe oben) und aufs Handy bringen – per Kabel: `adb install -r app/build/outputs/apk/debug/app-debug.apk`;
   ohne Kabel: Datei kopieren (Cloud, USB, Mail) und auf dem Handy antippen.
2. Beim ersten Mal erlaubt Android die Installation aus „unbekannten Quellen“ für die App, mit der du die
   Datei öffnest (Einstellungen → Apps → Spezieller App-Zugriff → Unbekannte Apps installieren).
3. Beim Start auf „Zugriff erlauben“ tippen. Die Debug-APK ist mit dem Debug-Schlüssel signiert; für eine
   Veröffentlichung braucht es eine eigene Signaturkonfiguration (nicht Teil dieses Projekts).

## Berechtigungen

| Berechtigung | Wofür |
|---|---|
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (Android 13+), `READ_EXTERNAL_STORAGE` (nur bis Android 12L) | Fotos und Videos lesen – es werden keine Kopien angelegt |
| `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14+) | Teilzugriff („nur ausgewählte Fotos“) wird erkannt und unterstützt |
| `ACCESS_MEDIA_LOCATION` | GPS-Position aus den Original-EXIF-Daten (Orte). Ohne sie gibt es keine Orte |
| `INTERNET` | **nur** OpenStreetMap-Kartenkacheln und Ortsnamen (Reverse-Geocoding) |

Löschen läuft immer über die Systemabfrage von Android – dafür ist keine weitere Berechtigung nötig.
Teilzugriff: Nicht freigegebene Medien fehlen; weil sie dann nicht als „gelöscht“ gelten dürfen, wird im
Teilzugriff nichts als extern gelöscht markiert.

## Bekannte Grenzen

- **Nicht auf einem Gerät getestet** (siehe Hinweis oben). Der Compose-Wischtest unter `app/src/androidTest`
  ist geschrieben und kompiliert, wurde aber nicht ausgeführt.
- Screenshots werden an „screenshot“ im Ordner-/Dateinamen erkannt – eine sicherere Kennzeichnung bietet
  MediaStore nicht.
- Der Tagesbeginn ist Mitternacht in der Gerätezeitzone. Die Architektur kennt einen einstellbaren
  Tagesbeginn (`CaptureTime`), die Oberfläche dafür gibt es nicht.
- Nominatim (OpenStreetMap) verlangt einen identifizierenden User-Agent; er lautet
  `Fotosortierer/<Version> (Android; <Application-ID>)`. Wer die App weitergibt, sollte eine Kontaktadresse
  ergänzen (`AppContainer.userAgent`). Die Kachelnutzung der OSM-Server ist nur für interaktive Karten
  gedacht, nicht für Massen-Downloads.
- Videos im Raster werden über Coils Video-Decoder dargestellt; bei sehr vielen Videos kann das Scrollen
  langsamer sein als bei Fotos.
- Nach dem Löschen behält Android Medien rund 30 Tage im System-Papierkorb; bei aktiver Cloud-Sicherung
  (z. B. Google Fotos) kann das Löschen dort ebenfalls ankommen.
- Nicht umgesetzt, aber vorbereitet: Duplikat-/Serienerkennung per Perceptual Hash (zusätzliche Spalte/Tabelle
  im Schema möglich), Favoriten, konfigurierbarer Tagesbeginn.
- Datenbank-Version 1; Migrationen kommen mit der ersten Schemaänderung (Room exportiert das Schema nach
  `app/schemas/`).

## Praxistest mit vielen Fotos (Empfehlung)

Auf einem echten Gerät mit 10 000–50 000 Medien prüfen:

1. **Erststart:** Berechtigungsdialog (Voll- und Teilzugriff testen), Fortschritt „Medien werden erfasst“ und
   „Standorte werden gelesen“ auf der Startseite; die App bleibt währenddessen bedienbar.
2. **Sortieren:** 100 Karten hintereinander wischen (Flüssigkeit, Speicherverbrauch), Zoom, Videos, Rückgängig,
   App im Hintergrund beenden und wieder öffnen → gleiche Stelle.
3. **Neue Medien:** Foto aufnehmen → erscheint nach wenigen Sekunden als unbearbeitet; ein fertiger Tag wird
   wieder „teilweise“.
4. **Orte:** Karte öffnen, Zoomstufen, Cluster antippen; Flugmodus an → Koordinaten statt Namen; wieder an →
   Namen erscheinen.
5. **Papierkorb:** wenige Medien löschen (Systemdialog erscheint, Abbrechen lässt alles unverändert), danach
   mehr als 500 Medien in einem Zug (mehrere Blöcke).
6. **Randfälle:** SD-Karte entfernen (Medien dürfen nicht als gelöscht gelten), Dunkelmodus, Querformat,
   Schriftgröße „sehr groß“, TalkBack (Karten haben benutzerdefinierte Aktionen).

## Aufbau

```
fotosortierer/
├── core/            reine Kotlin/JVM-Logik (Statusaggregation, Zeit, Sync-Plan, Clustering, SQL, Wischgeometrie …)
├── app/             Android-App (Compose-UI, Room, DataStore, WorkManager, MediaStore, osmdroid)
├── tools/compile-check/   Kompilier-Prüfstand für :app ohne Android-SDK (siehe dortige README)
└── CLAUDE.md        Architektur, Entscheidungen, Versionen, Stand der Prüfung
```

Details und alle getroffenen Entscheidungen: [`CLAUDE.md`](CLAUDE.md).
