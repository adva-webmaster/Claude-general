# Änderungen

## 1.0.1
- Behoben: „Keine Videospur“ bei allen Dateien. FFmpegs Bibliotheken brauchen fünf weitere Bibliotheken
  (`libc++_shared`, `libcrypto.so.3`, `libexpat.so.1`, `libandroid-support`, `libandroid-posix-semaphore`), die im
  Python-Paket von youtubedl-android liegen; sie werden jetzt zusätzlich entpackt (Abhängigkeit `ytdl-python`, nicht transitiv).
- ffprobe-Fehler werden mit Grund angezeigt; beim Start läuft ein Selbsttest (`ffprobe -version`).

## 1.0.0
- Erste Fassung: AVI/MOV → MP4 mit FFmpeg, Verzeichniswahl, MP4 neben dem Original,
  Umpacken statt Neukodieren wenn möglich, Plausibilitätsprüfung (Größe/Laufzeit),
  Originale wahlweise nach dem Durchlauf löschen.
