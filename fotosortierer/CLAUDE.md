# CLAUDE.md – PixelSweeper (Ordner/Branch/Paket bleiben „fotosortierer“)

Native Android-App (Kotlin, Jetpack Compose, Material 3) zum schnellen Aussortieren von Fotos und
Videos per Wischgesten (Vorbild: „Slidebox"). UI-Sprache Deutsch (Standardsprache `de`, alle Texte in
`strings.xml`). Keine Analytics, kein Tracking.

> **Stand:** `:core` ist lokal getestet. Der Android-Teil wird bei GitHub Actions gebaut, getestet und gelintet
> (`.github/workflows/fotosortierer-apk.yml`; Lauf auf Branch `fotosortierer`: Build, Tests, Lint ohne Fehler).
> In der ursprünglichen Entwicklungsumgebung war `dl.google.com` gesperrt, daher entstanden zusätzlich die
> Hilfsmittel in `tools/` (Kompilier-Prüfstand, Room-SQL-Prüfung). Auf einem Gerät wurde die App noch nicht getestet.

## Neu in 1.5.0

- **Laufbalken** (`VideoSeekBar`, `VideoControl` in `ui/sort/VideoControl.kt`): Zeit links/rechts, Tippen springt, Ziehen spult
  (Material-`Slider`). Er liegt als eigene Ebene über der Karte (in `CardStack`, links von der Ton-Taste), nicht in der
  Karte: Der Wisch-Handler der Karte beobachtet alle Zeiger in ihrem Bereich, Ziehen am Balken würde sonst wischen.
  `VideoControl` verbindet Abspieler (meldet Position/Länge, setzt `seeker`) und Balken – unabhängig vom Abspieler.
- **AVI & Co. mit Ton, aber schwarzem Bild:** ExoPlayer nutzt nur Android-Decoder; fehlt der Decoder für die Bildspur
  (typisch: AVI mit Motion-JPEG/DivX-Varianten), wird die Spur ignoriert und nur der Ton spielt. `VideoSurface` startet
  ExoPlayer und wechselt selbstständig zur Reserve **libvlc** (`VlcVideoSurface`, `org.videolan.android:libvlc-all`,
  LGPL), wenn (a) `Tracks` eine nicht unterstützte Bildspur melden, (b) ExoPlayer einen Fehler meldet oder (c) 2 s lang
  „bereit“ ohne Bildgröße. libvlc öffnet die Datei über einen Dateideskriptor. Wegen ~90 MB für alle Prozessorarten ist
  `ndk.abiFilters` auf `arm64-v8a` gesetzt (auf 32-Bit-/x86-Geräten läuft die App dann nicht).
  Der libvlc-Code ist **nur gegen eigene Stubs** (`tools/compile-check/stubs/LibVlc*.kt`) geprüft, nicht gegen die echte API.
- **Vorschau-Platzhalter:** Lädt ein Vorschaubild nicht (z. B. AVI-Kachel), zeigt `MediaImage` ein Foto-/Video-Symbol statt
  einer leeren Fläche. Existiert die Datei, wird sie dabei nicht entfernt (`purgeIfMissing` prüft die Datei).

## Neu in 1.4.0

- **Standorte-Lesen hängt nicht mehr:** Medien, deren Position sich nicht lesen ließ (`LocationReader.Result.Failed`),
  blieben ewig „ungeprüft“ (Anzeige „8583 von 8597“). Jetzt: zweiter Versuch im selben Durchgang; wenige (≤ 10 %,
  `LocationRetryPolicy`) dauerhaft Unlesbare gelten als ohne Standort, viele (allgemeines Problem, z. B. Berechtigung)
  bleiben offen für den nächsten Durchgang.
- **Sicherung Format 2 mit wählbaren Abschnitten** (`BackupSection`): Entscheidungen, **gelöschte Medien** (Tombstones mit
  Datum/Größe/Album/Pfad – stellen „gelöscht“ und „Speicher freigegeben“ wieder her), Standorte + Ortsnamen
  (`geocode_cache`), Einstellungen, Ordner (Favoriten/ausgeblendet). Export und Import zeigen eine Seite mit Schaltern
  (`BackupSectionsDialog`); beim Import ist nur wählbar, was die Datei enthält (`BackupManager.read` liest zuerst, `apply`
  wendet danach an). Gelöschte werden als Tombstones im künstlichen Volume `restored` mit negativen IDs angelegt (keine
  Kollision, der Abgleich fasst sie nie an); Duplikate (gleicher Name/Zeit/Größe) werden mengenweise übersprungen
  (`BackupPlanner.planDeleted`). Entscheidungen/Positionen ordnen nur vorhandene Medien zu (Tombstones ausgenommen).
  Format-1-Dateien (nur Entscheidungen + Einstellungen) bleiben importierbar. Der Import setzt `trashHintShown` nicht mehr zurück.

## 1.3.1: feste Debug-Signatur

GitHub-Runner sind frische Maschinen: Ohne feste Signatur erzeugt jeder Bau einen neuen Debug-Schlüssel, und Android
meldet beim Update „Konflikt mit der bestehenden App“. Darum liegt `app/debug.keystore` (Standard-Debug-Zugangsdaten
`android`/`androiddebugkey`, absichtlich eingecheckt) im Repository und ist in `app/build.gradle.kts` als
`signingConfigs.debug` eingetragen. Einmalig nötig: Versionen mit anderer Signatur (alle bis 1.3.0) vorher deinstallieren
(Entscheidungen vorher per Einstellungen → Sicherung exportieren sichern). Für Play später einen eigenen Upload-Schlüssel
verwenden, nicht diesen.

## Neu in 1.3.0

- **Verzeichnisbaum:** Spalte `media_state.relativePath` (MediaStore `RELATIVE_PATH`, normalisiert wie `Pictures/Urlaub/`;
  Room-Version 2, `MIGRATION_1_2` = `ALTER TABLE … ADD COLUMN … DEFAULT ''`; der nächste Abgleich füllt sie, weil
  `SyncPlanner.changed` den Pfad vergleicht). `MediaFilter.pathPrefix` = Verzeichnis samt Unterverzeichnissen
  (`substr(relativePath,1,len) = prefix`, kein LIKE → keine Sonderzeichenprobleme; Token `p=` im `FilterCodec`).
  Baum, Aufklappen und Suche: `core/DirectoryTree` (getestet); Anzeige als zweiter Modus (`FilterChip`) in `ui/folders`.
  Bewusst einfach: Verzeichnisse mit gleichem Pfad auf verschiedenen Volumes (Telefon/SD) werden zusammengefasst.
- **Umbenennung:** Die App heißt überall PixelSweeper (Anzeigename, Texte, Export-Datei `pixelsweeper-sicherung.json`,
  Sicherungskennung – alte Sicherungen mit „Fotosortierer“ bleiben importierbar, Theme, Workflow-/Artefaktname, APK-Datei
  `PixelSweeper-<Version>-debug.apk`). Unverändert bleiben technische Namen: Ordner/Branch, Paket und Application-ID
  `de.ingvoss.fotosortierer`, Datenbankdatei `fotosortierer.db`, Workflow-Dateiname.

## Behoben in 1.2.0 (gemeldet beim Test auf dem Handy mit 1.1.0)

- **Tageszahlen im Kalender abgeschnitten:** Tageszellen haben jetzt eine Mindesthöhe statt festem Seitenverhältnis
  (wachsen bei großer Schrift mit); der heutige Tag ist umrandet.
- **Medien ohne Vorschau / Absturz beim Öffnen in anderer App:** Ursache waren Karteileichen – Dateien, die mit einer
  anderen App gelöscht wurden, die der MediaStore aber noch listet. Sie werden jetzt aus der Datenbank entfernt (siehe
  „Auf anderem Weg gelöschte Medien“ unten). `startActivity` fängt zusätzlich `SecurityException` ab.
- **Neu:** Taste „Heute“ im Kalender (springt zum aktuellen Monat; im laufenden Monat ausgeblendet).

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
- **Sync-Sicherheit:** Medien nicht verfügbarer Volumes (ausgeworfene SD-Karte) werden nie entfernt;
  ein komplett leerer Scan entfernt nichts (`SyncPlanner`).
- **Auf anderem Weg gelöschte Medien:** Verschwindet ein Medium aus dem MediaStore (oder fehlt seine Datei trotz
  Eintrag), wird seine Zeile aus der Datenbank **gelöscht** (`SyncPlan.removals`) – kein Tombstone mehr. Nur was die
  App selbst löscht, bleibt als `GELOESCHT` in der Statistik („gelöscht“, „Speicher freigegeben“). Dateiprüfung:
  `MediaStoreScanner.checkFiles` (Pfad `DATA`, `File.exists`) beim App-Start (höchstens alle 30 min, nur bei vollem
  Zugriff); `MissingFileGuard` verwirft den Befund, wenn mehr als die Hälfte eines Volumes „fehlt“ (Speicher gerade
  nicht lesbar). Zusätzlich löst jeder Ladefehler einer Vorschau (`MediaImage`) und „In anderer App öffnen“ die
  Einzelprüfung `MissingMediaCleaner.purgeIfMissing` aus (öffnet den URI; nur eindeutiges „nicht vorhanden“ entfernt,
  fehlende Rechte nie); die Sortieransicht lädt danach ihre Warteschlange neu.
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
- **Schreibzugriffe im App-Scope:** Entscheidungen, Korrekturen, Einstellungen und bestätigte Löschblöcke werden
  im `AppContainer.appScope` geschrieben, nicht im `viewModelScope` – wer nach dem Wischen sofort zurückgeht,
  verliert nichts. Die Oberfläche lädt danach im `viewModelScope` nach.
- **Optimistische Oberfläche:** `SortViewModel.decide` entfernt die Karte sofort, schreibt dann der Reihe nach
  (Mutex) in Room und lädt die Warteschlange nach; noch nicht geschriebene Entscheidungen (`pending`) werden aus
  Nachladeergebnissen herausgefiltert.
- **Teilzugriff (Android 14):** Der Sync darf bei `PARTIAL` nichts als „verschwunden“ entfernen (nicht freigegebene
  Medien fehlen im Scan) und prüft keine Dateien. Nur `FULL` entfernt Medien.
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
- **Sicherung (JSON, Format 2):** Abschnitte wählbar (siehe „Neu in 1.4.0“); Zuordnung beim Import über
  (Name, Aufnahmezeit, Größe), bei Mehrdeutigkeit zusätzlich (Volume, ID) – `BackupPlanner` in `:core`.
- **„Datenbank neu aufbauen“** verwirft Standorte/Orte/Ortsnamen und gleicht neu ab; Entscheidungen bleiben.
- **Build-Dateien:** Plugins stehen mit `apply false` im Wurzel-`build.gradle.kts` (ein Classloader für Kotlin/
  AGP/KSP/Compose). Im Kern-Modus liest Gradle stattdessen `build-core.gradle.kts` (siehe `settings.gradle.kts`).
- **Karte ↔ Kalender (Scopes):** Kalender-Routen tragen einen Ortsfilter (`calendar/{scope}/…`), die Karte einen
  Zeitfilter (`places/{scope}`); das Token ist ein `FilterCodec`-Filter. Monats-/Tages-/Ortsaggregationen laufen dafür
  als dynamische Abfragen (`MediaSql.groupedMonths/groupedDays/groupedPlaces`). `MediaFilter` kennt dafür `localYear`
  und `clusterIds` (mehrere Orte, z. B. ein Kartencluster).
- **Erneutes Sortieren:** Hat der Filter eine Statusangabe, ist die Sitzung ein Review (`QueuePhase.ERNEUT`): alle
  Medien dieser Status mit `statusChangedAt < Sitzungsbeginn`; in der Sitzung Entschiedenes fällt heraus (keine
  Endlosschleife, auch bei gleichem Status). Rückgängig stellt `statusChangedAt` wieder her.
- **Keine "eingefrorenen" Handler:** Handler, die den aktuellen Zustand brauchen (Aktionstasten der Sortieransicht,
  Reiterwechsel), lesen ihn über `rememberUpdatedState` – ein beim ersten Mal gemerktes Lambda bediente sonst die
  längst abgeflogene erste Karte (Tasten wirkten nur einmal).
- **Kalender „Heute“:** `openToday(scope)` in `AppRoot` navigiert zu `month/<heute>` und räumt per `popUpTo(Routes.CALENDAR)`
  die Ebenen darüber ab (Zurück → Jahresliste); der Ortsfilter bleibt erhalten.
- **Navigation:** Untere Leiste (Start, Kalender, Orte, Ordner, Papierkorb), ab 600 dp Breite
  `NavigationRail`. Sortieren, Detail, Einführung und Einstellungen (Zahnrad auf der Startseite) sind Vollbild.
- **Ordner:** Ein Ordner ist das Album (`album` = Bucket-Name); gleichnamige Ordner verschiedener Speicher werden
  zusammengefasst (bewusst einfach gehalten, kein voller Pfad). Favoriten (`favoriteAlbums`) und Ausblenden
  (`excludedAlbums`, setzt `hidden`) liegen in den Einstellungen und im JSON-Export; Ausblenden entfernt den
  Favoritenstatus (Alben sind nur der Ordnername; für beliebige Verzeichnisse samt Unterverzeichnissen gibt es den
  Verzeichnisbaum, siehe „Neu in 1.3.0“). Reihenfolge/Suche/Aufbau der Liste: `core/FolderOrder` (getestet), Datenstrom: `FolderRepository`.
  Langtipp-Menü per `combinedClickable` + `DropdownMenu` in `ui/folders/FoldersScreen.kt`.
- **Design:** Markenfarben Dunkelrot (`BrandRed` #8E1B20 / hell #E4575C) und Anthrazit (#2B3035) stecken nur in den Material-Farbschemata (`ui/theme/Theme.kt`, auch Formen und Typografie). Die Statusfarben (Okabe–Ito) bleiben davon unberührt. Die Webseite des Nutzers war aus der Sandbox nicht erreichbar; exakte Hex-Werte lassen sich dort an einer Stelle tauschen.
- **Karten-Kopfzeile:** Info-Leiste liegt als Überlagerung (`PlacesInfo`) über der Karte, damit sich die Kartenhöhe beim Bewegen nie ändert.
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
- `./gradlew -Dfoto.coreOnly=true :core:test` – **168 Tests** (Statusaggregation, Zeitzonen/Tagesgrenzen/
  Fallback, Sync-Planung, Clustering inkl. Performance mit 60 000 Punkten, Bildschirm-Clustering, Undo-Stapel,
  ISO-6709, Wischgeometrie und -belegung, Zoom-Rechnung, Filter-Codec, Monatsraster, Formate, Ortsnamen,
  Medien-Klassifizierer, Sicherungs-Zuordnung, alle SQL-Aggregationen/Warteschlangen gegen echtes SQLite via
  sqlite-jdbc). `:core` kompiliert mit `allWarningsAsErrors`.
- `tools/compile-check`: `:app` (main, Unit-Tests, androidTest) kompiliert fehlerfrei gegen Compose-Desktop 1.7.3,
  Android-Framework (API 37), Coil, osmdroid und Stubs für die nur auf Google Maven liegenden Bibliotheken.
  Das beweist Syntax, Typen und interne Konsistenz – nicht die Übereinstimmung der Stubs mit den echten
  Bibliotheken.
- `tools/room-sql-check.py`: prüft alle 45 `@Query`-Texte der DAOs gegen echtes SQLite (gültiges SQL, Parameter
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
