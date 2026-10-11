# Kompilier-Prüfstand für `:app`

Hilfsmittel, mit dem sich der Kotlin-Quelltext von `:app` **ohne Android-SDK und ohne Google Maven** gegen
echte Bibliotheken kompilieren lässt. Es ersetzt **nicht** `./gradlew assembleDebug` – es fängt aber
Syntax-, Typ- und Referenzfehler ab.

Verwendet werden (alle von Maven Central):

- Compose Multiplatform 1.7.3 (Desktop-Jars; gleiche `androidx.compose.*`-Pakete wie Android),
- das Android-Framework (`org.robolectric:android-all`, API 37),
- Coil 3.6.3 und osmdroid 6.1.20 (echte Klassen).

Für Bibliotheken, die nur auf Google Maven liegen (Room, Paging, DataStore, WorkManager, Media3, Navigation,
Lifecycle, Activity), liegen Stubs in `stubs/`, die die verwendeten Signaturen nachbilden. Die Stubs beruhen
auf den dokumentierten APIs; ein Fehler dort würde erst im echten Build auffallen.

```bash
./fetch-libs.sh              # einmalig: Android-Framework, Coil, osmdroid herunterladen (~220 MB)
python3 gen-r.py             # R- und BuildConfig-Klasse aus strings.xml erzeugen
./run.sh compileTestKotlin   # :app (main + Tests) und :core kompilieren
```

Ausführen lassen sich damit keine Compose-Tests: Zur Laufzeit braucht Compose die AndroidX-Basisbibliotheken
(collection, lifecycle …), die nur auf Google Maven liegen.
