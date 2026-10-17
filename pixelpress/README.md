# PixelPress

Wandelt **AVI** und **MOV** in **MP4** um. Verzeichnis wählen, Dateien ankreuzen, starten – die MP4 liegt
jeweils neben dem Original. Optional werden die Originale nach dem Durchlauf gelöscht, aber nur die, bei
denen die Plausibilitätsprüfung (Dateigröße und Laufzeit) bestanden wurde.

## Funktionsweise
- Passt das Material schon in MP4 (H.264/H.265 + AAC/MP3/AC-3), wird nur umgepackt (schnell, verlustfrei).
  Sonst wird mit x264 (Qualität wählbar) und AAC neu kodiert.
- Ausgabe zuerst als `<name>.mp4.part`, nach bestandener Prüfung umbenannt. Besteht die Prüfung nicht,
  wird die Ausgabe verworfen und das Original bleibt unberührt.
- Namenskonflikte: `name.mp4`, sonst `name_avi.mp4`, `name_avi_2.mp4` …; optional wird übersprungen, wenn `name.mp4` existiert.
- Engine: FFmpeg 7.1 (Termux-Build, GPL v3, mit x264), als eigener Prozess gestartet.

## Bauen
```bash
./gradlew -Dpp.coreOnly=true :core:test   # reiner Kern, ohne Android-SDK
./gradlew assembleDebug                   # APK (Android-SDK 37 nötig)
```
Nur arm64-Geräte (`abiFilters`); braucht „Zugriff auf alle Dateien“ (`MANAGE_EXTERNAL_STORAGE`) – daher nicht für Google Play gedacht.

## Lizenz / Hinweise
FFmpeg und x264 stehen unter **GPL v3**. Wer die APK weitergibt, muss den Quelltext (auch dieser App) unter GPL anbieten
– siehe `NOTICE.md`. Empfehlung: den App-Code ebenfalls unter GPL-3.0 stellen. Keine Rechtsberatung.
Auf einem Gerät wurde die App noch nicht getestet.
