# CLAUDE.md – Fotosortierer

Native Android-App (Kotlin, Jetpack Compose, Material 3) zum schnellen Aussortieren von Fotos und
Videos per Wischgesten (Vorbild: „Slidebox"). UI-Sprache Deutsch (Standardsprache `de`, alle Texte in
`strings.xml`). Keine Analytics, kein Tracking.

> **Wichtiger Hinweis zum Stand:** Die Entwicklungsumgebung, in der dieses Projekt entstanden ist,
> hatte **keinen Zugriff auf `dl.google.com`** (Egress-Policy, HTTP 403). Damit waren weder das
> Android-SDK noch Google Maven (AGP, AndroidX, Compose, Room, Media3, …) erreichbar. Der Android-Teil
> (`:app`) konnte dort daher **nicht gebaut** werden. Voll gebaut und getestet wurde `:core`
> (siehe „Verifikation"). Details und Build-Anleitung: `README.md`.

## Modulstruktur

```
fotosortierer/
├── core/   reines Kotlin/JVM, KEINE Android-Abhängigkeit – gesamte testbare Logik
└── app/    Android-App (Compose, Room, DataStore, WorkManager, Coil, Media3, osmdroid)
```

Gedanke: Alles, was Fachlogik ist (Statusaggregation, Zeit-/Tagesgrenzen, Sync-Abgleich,
Clustering, Undo, SQL-Bausteine), liegt in `:core` und ist mit `./gradlew :core:test` ohne Gerät und
ohne Android-SDK testbar. `:app` enthält nur Android-/UI-Klebecode darum herum.

## Build-Befehle

```bash
./gradlew assembleDebug        # Debug-APK → app/build/outputs/apk/debug/
./gradlew test                 # Unit-Tests (:core und :app)
./gradlew lint                 # Android Lint
./gradlew connectedDebugAndroidTest   # Instrumented/Compose-Tests (Gerät/Emulator nötig)

# Nur der reine Kotlin-Kern, ohne Android-SDK / Google Maven:
./gradlew -Dfoto.coreOnly=true :core:test
```

`-Dfoto.coreOnly=true` (oder Umgebungsvariable `FOTO_CORE_ONLY=1`) nimmt `:app` aus dem Build und
verwendet nur Maven Central/Plugin-Portal (siehe `settings.gradle.kts`).

Die Application-ID steht zentral in `gradle.properties` (`app.applicationId`, Vorgabe
`de.ingvoss.fotosortierer`) und lässt sich per `-Papp.applicationId=…` überschreiben. Der Kotlin-
Namespace/Package bleibt `de.ingvoss.fotosortierer`.

## Versionen (geprüft am 11.10.2026)

| Bereich | Version | Quelle |
|---|---|---|
| Android Gradle Plugin | 9.4.0 | developer.android.com/build/releases/gradle-plugin |
| Gradle | 9.6.0 (Minimum für AGP 9.4) | ebenda; Wrapper-SHA-256 aus dem Download berechnet |
| Kotlin | 2.4.21 | maven-metadata.xml (Maven Central) |
| KSP | 2.3.12 | Maven Central + GitHub-Release-Notes (AGP-9-/Kotlin-2.4-Fixes) |
| Compose BOM | 2026.09.00 (Material3 1.4.0, UI/Foundation 1.12.1) | developer.android.com/…/bom-mapping |
| Room 2.8.5, DataStore 1.2.1, Paging 3.5.2, WorkManager 2.12.0, Navigation 2.10.2, Lifecycle 2.11.0, Activity 1.13.0, Core 1.19.1, Media3 1.11.1 | | developer.android.com/jetpack/androidx/versions |
| Coil 3.6.3, osmdroid 6.1.20, Coroutines 1.11.0 | | Maven Central |
| compileSdk / targetSdk | 37 (Android 17), minSdk 30 | AGP 9.4 unterstützt maximal API 37; Play verlangt ≥ 36 |

Google-Maven-Versionen konnten nur über die offiziellen Release-Seiten (nicht über
`maven-metadata.xml`) geprüft werden. Falls das SDK-Paket `android-37` fehlt: `compileSdk`/`targetSdk`
in `app/build.gradle.kts` (eine Stelle) auf 36 senken.

Room 3.0 (`androidx.room3`) existiert bereits, wird aber bewusst nicht verwendet: andere Package-
Namen/API, Room 2.8.5 ist die etablierte stabile Linie.

## Architektur-Entscheidungen

- **Single-Activity, Navigation Compose mit String-Routen.** Typsichere Routen bräuchten das
  Serialization-Plugin; für diesen Umfang unnötiges Risiko.
- **DI manuell** (`AppContainer` in der `Application`, ViewModel-Factories über
  `viewModelFactory { initializer { … } }`). Hilt hätte KSP-/AGP-9-Kompatibilität und Generierungs-
  Magie hinzugefügt, ohne hier einen Nutzen zu bringen.
- **AGP 9 „Built-in Kotlin":** Das Plugin `org.jetbrains.kotlin.android` wird nicht angewendet.
  Compose-Compiler-Plugin und KSP werden normal angewendet.
- **Karte: osmdroid** (OpenStreetMap-Kacheln, kein API-Key, Kachel-Cache, korrekter User-Agent).
  MapLibre wäre moderner (Vektorkarten), bräuchte aber einen Stil-/Tile-Anbieter (meist API-Key) –
  das widerspricht „kein API-Key". osmdroid hat kein eingebautes Clustering; deshalb zeichnet ein
  eigenes `Overlay` geclusterte Marker (Pixel-Distanz je Zoomstufe, siehe `ui/map`).
- **Medien aus MediaStore, keine Kopien.** Schlüssel eines Mediums ist `(volume, mediaStoreId)`,
  weil MediaStore-IDs nur je Volume eindeutig sind. Tabelle `media_state` hat einen Surrogat-PK
  `uid` und einen Unique-Index auf `(volume, mediaStoreId)`.
- **Status-Codes in der DB als Int** (`MediaStatus.code`), damit SQL lesbar bleibt
  (`status = 0` usw.). Änderungen nur mit Migration.
- **Tag/Monat/Ort sind abgeleitet, nicht gespeichert.** Zustand = Aggregation der Medienstatus per
  SQL (`GROUP BY`). Dadurch setzt ein neues Medium in einem erledigten Tag diesen automatisch auf
  „teilweise" zurück – es gibt keinen separaten Tagesstatus, der veralten könnte.
- **Ausgeblendete Medien (`hidden`)**: Einstellungen „Medientypen" und „auszuschließende Alben"
  setzen die Spalte `hidden` per `UPDATE`; alle Abfragen filtern `hidden = 0`. So bleiben die
  Aggregations-Queries statisch (Room-`@Query`) und schnell.
- **Sitzung = Filter + Warteschlange.** Eine Sitzung ist ein `MediaFilter` (Tag/Monat/Ort/Album/
  Typ/Status). Die Warteschlange ist eine Abfrage mit `LIMIT 4` (aktuelles Medium + 3 Vorlade-
  Karten) auf `status = UNBEARBEITET`; danach Phase 2 `status = ZURUECKGESTELLT AND statusChangedAt
  < phase2Start` (erneut Zurückgestellte kommen in derselben Sitzung nicht noch einmal dran – keine
  Endlosschleife). Jede Entscheidung wird sofort in Room geschrieben; daraus folgt die
  „Position" nach Neustart automatisch. Filter, Beginn der Sitzung und Phase-2-Beginn liegen in
  DataStore.
- **Undo** (`UndoStack`, 50 Einträge) merkt je Aktion `(uid, alter Status, alte Änderungszeit)`.
- **Löschen nur mit Systembestätigung:** `MediaStore.createTrashRequest`, in Blöcken à 500. Erst
  nach `RESULT_OK` wird der Block als `GELOESCHT` markiert. Medien, die später aus dem System-
  Papierkorb zurückkommen, werden vom Sync „wiederbelebt" (UNBEARBEITET).
- **Berechtigungen versionsabhängig:** ab API 33 `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO`
  (+ `READ_MEDIA_VISUAL_USER_SELECTED` deklariert, Teilzugriff wird erkannt), darunter
  `READ_EXTERNAL_STORAGE` (maxSdk 32), da minSdk 30 ist. `ACCESS_MEDIA_LOCATION` für EXIF-GPS
  (`MediaStore.setRequireOriginal`). `INTERNET` nur für Kartenkacheln und Reverse-Geocoding.
- **Sync-Sicherheit:** Medien nicht verfügbarer Volumes (ausgeworfene SD-Karte) werden nie zu
  Tombstones; ein komplett leerer Scan löscht nichts (`SyncPlanner`).
- **Ortsbildung:** Leader-Clustering mit Raster-Index (`GeoClusterer`, Standardradius 1 km,
  einstellbar). Namen pro Cluster (nicht pro Foto): Android-`Geocoder` → Nominatim (≤ 1 Anfrage/s,
  User-Agent, dauerhaft in Room gecacht) → Koordinaten-Bezeichnung. Läuft im WorkManager.
- **Konfigurierbarer Tagesbeginn** ist nicht in der UI, aber in `CaptureTime` als Parameter
  vorgesehen. Duplikat-/Serienerkennung (Perceptual Hash) und Favoriten sind nicht umgesetzt; das
  Schema lässt zusätzliche Spalten/Tabellen ohne Umbau zu.

## Statuslogik (Tag, Monat, Ort)

- **Unbearbeitet:** kein Element ist bearbeitet (BEHALTEN/PAPIERKORB/GELOESCHT).
- **Teilweise:** mindestens eines bearbeitet **und** mindestens eines UNBEARBEITET oder
  ZURUECKGESTELLT.
- **Vollständig:** kein Element UNBEARBEITET oder ZURUECKGESTELLT. Nur gelöschte Elemente → vollständig.
- Zurückgestellte zählen **nicht** als bearbeitet und erhalten zusätzlich ein eigenes Badge.
- Leere Gruppen sind neutral (`ProgressState.LEER`).
- Implementierung: `core/ProgressState.kt` (`StatusCounts.state`), erschöpfend gegen die Definition
  getestet (`StatusAggregationTest`).

## Verifikation

Ausgeführt und grün:
- `./gradlew -Dfoto.coreOnly=true :core:test` – 73 Tests (Statusaggregation, Zeitzonen/Tagesgrenzen/
  Fallback, Sync-Planung, Clustering inkl. Performance mit 60 000 Punkten, Undo-Stack, ISO-6709,
  alle SQL-Aggregationen/Warteschlangen gegen echtes SQLite via sqlite-jdbc). `:core` kompiliert mit
  `allWarningsAsErrors`.

Nicht ausführbar in der Entwicklungsumgebung (kein SDK/Google Maven): `assembleDebug`, `lint`,
`test` für `:app`, Instrumented-/Compose-Tests. Siehe README für exakte Schritte.
