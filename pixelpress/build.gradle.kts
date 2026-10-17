// Plugin-Versionen stehen im Versionskatalog (gradle/libs.versions.toml). Die Plugins werden hier einmal
// mit "apply false" geladen, damit sie in allen Modulen denselben Classloader teilen.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
