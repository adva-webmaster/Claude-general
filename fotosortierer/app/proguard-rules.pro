# Room, WorkManager, Compose und Media3 bringen ihre eigenen Consumer-Regeln mit.

# osmdroid lädt Kachelquellen teils reflektiv.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Worker werden von WorkManager per Klassenname instanziiert.
-keep class de.ingvoss.fotosortierer.work.** extends androidx.work.ListenableWorker { *; }

# libvlc ruft Java-Methoden aus nativem Code auf.
-keep class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.**
