# CLAUDE.md – Fotosortierer

Native Android-App (Kotlin, Jetpack Compose, Material 3) zum schnellen Aussortieren von Fotos und
Videos per Wischgesten (Vorbild: „Slidebox"). UI-Sprache Deutsch (Standardsprache `de`, alle Texte in
`strings.xml`). Keine Analytics, kein Tracking.

> **Wichtiger Hinweis zum Stand:** Die Entwicklungsumgebung, in der dieses Projekt entstanden ist,
> hatte **keinen Zugriff auf `dl.google.com`** (Egress-Policy, HTTP 403). Damit waren weder das
> Android-SDK noch Google Maven (AGP, AndroidX, Compose, Room, Media3, …) erreichbar. `:core` wurde voll
> gebaut und getestet; `:app` wurde nur **gegen Stubs/Ersatz-Jars kompiliert** (`tools/compile-check/`), aber
> **nie mit AGP gebaut**, nie gelintet, nie auf einem Gerät gestartet. Siehe „Verifikation“ und `README.md`.

## Modulstruktur

```
fotosortierer/
├── core/   reines Kotlin/JVM, KEINE Android-Abhängigkeit – gesamte testbare Logik
├── app/    Android-App (Compose, Room, DataStore, WorkManager, Coil, Media3, osmdroid)
└── tools/compile-check/   Kompilier-Prüfstand für :app ohne Android-SDK
```

Gedanke: Alles, was Fachlogik ist (Statusaggregation, Zeit-/Tagesgrenzen, Sync-Abgleich,
Clustering, Undo, SQL-Bausteine, Wischgeometrie, Zoom-Rechnung, Sicherungs-Zuordnung), liegt in `:core` und
ist mit `./gradlew :core:test` ohne Gerät und ohne Android-SDK testbar. `:app` enthält nur Android-/UI-Klebecode
darum herum.

`app/src/main/kotlin/de/ingvoss/fotosortierer/`:

| Paket | Inhalt |
|---|---|
| (Wurzel) | `FotoApp` (Application, Coil-Factory), `AppContainer` (manuelle DI, MediaStore-Beobachter), `MainActivity` |
| `data/db` | Room: `MediaStateEntity`, `PlaceEntity`, `GeocodeCacheEntity`, DAOs (SQL-Texte aus `MediaSql`), Zeilenklassen |
| `data/settings` | `SettingsRepository` (DataStore), `SessionStore` (laufende Sitzung), `AppSettings` |
| `data/media` | `MediaStoreScanner`, `MediaSyncer`, `LocationReader`, `LocationIndexer`, `PlaceBuilder`, `PlaceNamer`, `GeocodingService`, `MediaRepository`, `TrashRepository`, `FilterLabeler`, `MediaAccess` |
| `data/backup` | `BackupManager` (JSON per `JsonWriter`/`JsonReader`, SAF) |
| `work` | `LocationWorker`, `GeocodeWorker`, `WorkScheduler` |
| `ui/…` | `theme` (Farben, eigene Linien-Icons), `components`, `sort` (Wischkarte, Sortier-VM), `calendar`, `places` (Karte, Overlay, Liste), `detail` (Raster/Korrektur), `trash`, `home`, `settings`, `onboarding`, `AppRoot` (Navigation) |

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

### Weitere Entscheidungen (App)

- **Eigene Symbole statt `material-icons-extended`:** `ui/theme/Icons.kt` zeichnet ~20 Linien-Symbole als
  `ImageVector`. Die Icon-Bibliothek ist groß und verlangsamt Debug-Builds; die Version wäre zudem über das
  BOM nicht von mir prüfbar gewesen.
- **Farben (Okabe–Ito, farbenblind-sicher):** unbearbeitet #D55E00 (Ring gestrichelt), teilweise #F0E442/E0B800
  (Teil-Ring), vollständig #0072B2 (gefüllt mit Haken), Zurückgestellt-Punkt #CC79A7; Medien: behalten #009E73,
  Papierkorb #D55E00, zurückgestellt #CC79A7. Farbe ist nie das einzige Merkmal (Form, Zahl, Symbol, Text).
  Dunkel-Varianten in `FotoColors.Dark`. Vollständige Zeiträume wirken gedämpft.
- **Wischkarte (`ui/sort/SwipeCard.kt`):** zwei `pointerInput`-Modifier – außen `detectTapGestures`
  (Tippen/Doppeltipp-Zoom), innen eine eigene Gestenschleife (Wischen, Zwei-Finger-Zoom, Verschieben). Wischen
  konsumiert die Ereignisse und bricht damit den Tipp ab. Schwelle = 30 % der kürzeren Kartenseite oder
  schnelle Geste ab 25 % der Schwelle (`SwipeGeometry`). Während des Zoomens ist Wischen aus. `beginExit()`
  stellt sicher, dass eine Karte nie zwei Entscheidungen auslöst. Für TalkBack hat die Karte
  `customActions`; Schaltflächen sind räumlich wie die Richtungen angeordnet.
- **Kartenstapel:** oberste Karte + zwei sichtbare Hintergrundkarten + eine unsichtbar vorgeladene (4 Abfrage-
  ergebnisse = „Vorladen 2–3“). Coil lädt auf Layoutgröße herunterskaliert, erst beim Zoomen einmal mit 2400 px.
- **Optimistische Oberfläche:** `SortViewModel.decide` entfernt die Karte sofort, schreibt dann der Reihe nach
  (Mutex) in Room und lädt die Warteschlange nach; noch nicht geschriebene Entscheidungen (`pending`) werden aus
  Nachladeergebnissen herausgefiltert.
- **Teilzugriff (Android 14):** Der Sync darf bei `PARTIAL` keine Tombstones setzen (nicht freigegebene Medien
  fehlen im Scan). Nur `FULL` vergibt Tombstones.
- **Hintergrundarbeit:** `MediaSyncer` (Scan, schnell, bei Start + `ContentObserver` mit 2 s Ruhezeit),
  `LocationWorker` (EXIF/ISO-6709, 4 parallele Lesevorgänge, Stapel zu 200, ordnet nach jedem Stapel Orte zu),
  `GeocodeWorker` (nur mit Netz, Backoff 60 s). Fortschritt kommt aus der Datenbank (`observeLocationProgress`),
  nicht aus WorkManager-Progress. Einreihung mit `APPEND_OR_REPLACE`, damit während eines Laufs neu
  hinzugekommene Medien nicht untergehen.
- **Orte:** Radius ändern → `PlaceBuilder.rebuild` (Cluster-IDs neu, Namen aus `geocode_cache`, Rasterzelle
  ≈ 110 m). Geocoding: Android-`Geocoder` → Nominatim → Koordinaten. Netzfehler zählen nicht als Ergebnis
  (spätere Wiederholung); „kein Ergebnis“ wird dauerhaft gemerkt.
- **Kartenclustering:** `PlaceClusterOverlay` rechnet pro Zeichnen mit `ScreenClusterer` (Raster, Pixelradius =
  Markerdurchmesser). Antippen eines Clusters zoomt hinein; auf höchster Zoomstufe öffnet sich das Sheet mit
  mehreren Orten.
- **Löschen:** `TrashViewModel` fragt Block für Block (`createTrashRequest`, je Volume, ≤ 500); `RESULT_OK` →
  `GELOESCHT`, sonst Abbruch ohne Änderung. Einmaliger Hinweisdialog (`trashHintShown`).
- **Sicherung (JSON):** nur BEHALTEN/PAPIERKORB/ZURUECKGESTELLT + Einstellungen; Zuordnung beim Import über
  (Name, Aufnahmezeit, Größe), bei Mehrdeutigkeit zusätzlich (Volume, ID) – `BackupPlanner` in `:core`.
- **„Datenbank neu aufbauen“** verwirft Standorte/Orte/Ortsnamen und gleicht neu ab; Entscheidungen bleiben.
- **Build-Dateien:** Plugins stehen mit `apply false` im Wurzel-`build.gradle.kts` (ein Classloader für Kotlin/
  AGP/KSP/Compose). Im Kern-Modus liest Gradle stattdessen `build-core.gradle.kts` (siehe `settings.gradle.kts`).
- **Navigation:** Untere Leiste (Start, Kalender, Orte, Papierkorb, Einstellungen), ab 600 dp Breite
  `NavigationRail`. Sortieren, Detail und Einführung sind Vollbild.
- **Manifest:** `allowBackup=false` (Entscheidungen sichert der JSON-Export); von osmdroid mitgebrachte
  Berechtigungen `WRITE_EXTERNAL_STORAGE`/`ACCESS_WIFI_STATE` werden per `tools:node="remove"` entfernt.

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
- `./gradlew -Dfoto.coreOnly=true :core:test` – **129 Tests** (Statusaggregation, Zeitzonen/Tagesgrenzen/
  Fallback, Sync-Planung, Clustering inkl. Performance mit 60 000 Punkten, Bildschirm-Clustering, Undo-Stapel,
  ISO-6709, Wischgeometrie und -belegung, Zoom-Rechnung, Filter-Codec, Monatsraster, Formate, Ortsnamen,
  Medien-Klassifizierer, Sicherungs-Zuordnung, alle SQL-Aggregationen/Warteschlangen gegen echtes SQLite via
  sqlite-jdbc). `:core` kompiliert mit `allWarningsAsErrors`.
- `tools/compile-check`: `:app` (main, Unit-Tests, androidTest) kompiliert fehlerfrei gegen Compose-Desktop 1.7.3,
  Android-Framework (API 37), Coil, osmdroid und Stubs für die nur auf Google Maven liegenden Bibliotheken.
  Das beweist Syntax, Typen und interne Konsistenz – nicht die Übereinstimmung der Stubs mit den echten
  Bibliotheken.
- `tools/room-sql-check.py`: prüft alle 37 `@Query`-Texte der DAOs gegen echtes SQLite (gültiges SQL, Parameter
  ↔ Funktionsparameter, Ergebnisspalten ⊇ Konstruktorparameter der Ergebnisklasse) – Ersatz für die
  Compile-Zeit-Prüfung von Room.
- XML-Ressourcen wohlgeformt; jede im Code verwendete `R.string`/`R.plurals` existiert.

**Nicht** ausgeführt (kein SDK/Google Maven): `assembleDebug`, `lint`, `test` für `:app`, Instrumented-/Compose-
Tests, KSP/Room-Verarbeitung (SQL-Prüfung der DAOs zur Compile-Zeit), Start auf einem Gerät. Mögliche
Reibungspunkte beim ersten echten Build, in dieser Reihenfolge:
1. `compileSdk`/`targetSdk` 37 nicht installiert → auf 36 senken (`app/build.gradle.kts`).
2. Eine Bibliotheksversion im Katalog ist auf Google Maven (noch) nicht auffindbar → `gradle/libs.versions.toml`.
3. AGP-9-DSL-Eigenheiten (`kotlin { }` im App-Modul mit eingebautem Kotlin, KSP-Zusammenspiel).
4. Lint-Fehler (nicht Warnungen) in Compose-/Manifest-Prüfungen.
