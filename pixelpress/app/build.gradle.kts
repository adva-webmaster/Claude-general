import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// AGP 9 bringt Kotlin eingebaut mit ("built-in Kotlin"): das Plugin org.jetbrains.kotlin.android wird bewusst NICHT angewendet.

// Zentrale Application-ID: gradle.properties (app.applicationId) oder -Papp.applicationId=…
val appId: String = providers.gradleProperty("app.applicationId").getOrElse("de.ingvoss.pixelpress")

android {
    namespace = "de.ingvoss.pixelpress"
    compileSdk = 37

    defaultConfig {
        applicationId = appId
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        // Das FFmpeg-Paket bringt Programme für alle Prozessorarten mit (~140 MB). Heutige Handys sind arm64.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        // Feste Debug-Signatur (liegt bewusst im Repository; ein Debug-Schlüssel ist nicht geheim). Sonst erzeugt jeder
        // GitHub-Bau einen neuen Zufallsschlüssel, und Android lehnt Updates ab ("Konflikt mit vorhandener App").
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // FFmpeg wird als eigenes Programm gestartet; Verkleinern bringt hier nichts und würde die
            // Verweise des Pakets auf fehlende Klassen beanstanden.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // FFmpeg (libffmpeg.so, libffprobe.so) wird als Programm aus dem Bibliotheksordner gestartet; dafür müssen die
        // Dateien beim Installieren dort ausgepackt werden (nicht im APK komprimiert/ausgerichtet belassen).
        jniLibs { useLegacyPackaging = true }
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Nur die Programmdateien (libffmpeg.so, libffprobe.so, libffmpeg.zip.so) werden gebraucht; der Java-Teil des Pakets
    // gehört zu youtubedl-android und wird nicht benutzt (daher ohne weitere Abhängigkeiten).
    implementation(libs.ffmpeg.binaries) { isTransitive = false }

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
