// Wurzel-Build-Datei im Kern-Modus (-Dpp.coreOnly=true): nur das reine Kotlin-Modul, ohne AGP/Google Maven.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
