# Hinweise zu verwendeter Software (PixelSweeper)

Dieselben Angaben stehen in der App unter *Einstellungen → Lizenzen und Hinweise*.

## VLC-Engine (libVLC) – GNU LGPL 2.1

- Zweck: Reserve-Abspieler für Videos, die Android nicht darstellt (z. B. AVI mit Motion-JPEG). Maven: `org.videolan.android:libvlc-all`.
- Urheber: VideoLAN und die VLC-Autoren. Lizenz: GNU Lesser General Public License, Version 2.1 (Angabe im Maven-POM).
- Die Bibliothek wird **unverändert** und **dynamisch** (eigene `.so`-Dateien im APK) eingebunden.
- Quellcode: https://code.videolan.org/videolan/libvlcjni und https://code.videolan.org/videolan/vlc
- Austauschbarkeit (LGPL §6): Die App-Quellen liegen im Repository; die Bibliothek ist über `gradle/libs.versions.toml`
  (`vlc`) austauschbar, die App lässt sich damit neu bauen. In der App steht das Angebot, auf Anfrage über adva-unlimited.de die
  nötigen Dateien bereitzustellen – **diese Zusage muss ADVA Unlimited UG einhalten**.
- Lizenztext: `app/src/main/res/raw/license_lgpl_2_1.txt` (in der App einblendbar).
- Patente: VideoLAN übernimmt keine Patentlizenzen für Videoformate. Das ist bei gewerblicher Verbreitung (z. B. Play Store)
  vor der Veröffentlichung rechtlich zu prüfen.

## Weitere Bausteine

- AndroidX (Compose, Navigation, Room, DataStore, WorkManager, Paging, Media3/ExoPlayer), Coil, osmdroid, Kotlin/kotlinx.coroutines:
  Apache License 2.0 (`app/src/main/res/raw/license_apache_2_0.txt`).
- Kartendaten © OpenStreetMap-Mitwirkende (ODbL), https://www.openstreetmap.org/copyright. Ortsnamen: Android-Geocoder bzw. Nominatim.

Dies ist keine Rechtsberatung.
