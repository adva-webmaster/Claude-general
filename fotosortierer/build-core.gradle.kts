// Wurzel-Build-Datei im Kern-Modus (-Dfoto.coreOnly=true): nur das reine Kotlin-Modul, ohne AGP/Google Maven.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
