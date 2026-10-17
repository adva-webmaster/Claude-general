# Hinweise zu Drittsoftware

| Baustein | Lizenz | Quelle |
|---|---|---|
| FFmpeg 7.1 (ffmpeg/ffprobe, Termux-Build) | GPL v3 (Build mit `--enable-gpl --enable-version3`) | https://ffmpeg.org · https://github.com/termux/termux-packages |
| x264 | GPL v2 oder neuer | https://www.videolan.org/developers/x264.html |
| youtubedl-android (`io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1`, Verpackung) | GPL v3 | https://github.com/JunkFood02/youtubedl-android |
| Python-Paket von youtubedl-android (nur `libc++_shared.so`, `libcrypto.so.3` (OpenSSL, Apache 2.0), `libexpat.so.1` (MIT), `libandroid-support`/`libandroid-posix-semaphore` (Termux, MIT/BSD)) | verschiedene, siehe Quellen | https://github.com/termux/termux-packages |
| AndroidX / Jetpack Compose / Kotlin | Apache 2.0 | https://developer.android.com |

Die Programme werden unverändert als eigener Prozess gestartet. Weitergabe der APK verpflichtet zum Angebot des
passenden Quelltextes (FFmpeg/x264 und dieser App). Der Volltext der GPL v3 liegt der App bei (Info-Seite).
Patentlage für H.264/H.265/AAC vor gewerblicher Verbreitung prüfen lassen (keine Rechtsberatung).
