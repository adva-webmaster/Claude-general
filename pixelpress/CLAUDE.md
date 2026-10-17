# CLAUDE.md – PixelPress

Android-App (Kotlin, Compose, Material 3), UI Deutsch. AVI/MOV → MP4 mit FFmpeg (Prozess, Termux-Build aus
`io.github.junkfood02.youtubedl-android:ffmpeg`, entpackt nach `noBackupFilesDir/ffmpeg`).

- `:core` = reine Logik (Pfad-Mapping, Planung, Plausibilität, ffmpeg-Argumente, Parser); lokal testbar:
  `./gradlew -q --offline -Dpp.coreOnly=true :core:test`.
- `:app` = Android-Klebecode (`ConversionController` → `ConversionService` als Vordergrunddienst).
- Pflicht bei neuer Version: `versionName/Code` in `app/build.gradle.kts`, `res/values/release_notes.xml`, `CHANGELOG.md`.
- Der App-Teil wurde lokal nicht kompiliert (nur CI) und die FFmpeg-Ausführung nicht auf einem Gerät geprüft.
- Wichtige Einstellungen: `extractNativeLibs=true`, `jniLibs.useLegacyPackaging`, arm64-only, `MANAGE_EXTERNAL_STORAGE`.
- Löschen der Originale nur nach bestandener Plausibilitätsprüfung (`Plausibility`), nach dem Durchlauf, mit Rückfrage.
