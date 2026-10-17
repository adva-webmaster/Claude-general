// Kern-Modus: Mit -Dpp.coreOnly=true (oder Umgebungsvariable PP_CORE_ONLY=1) wird nur das
// reine Kotlin-Modul :core gebaut. Das ist nützlich in Umgebungen ohne Android-SDK/Google-Maven
// (z. B. CI-Sandboxes): `./gradlew -Dpp.coreOnly=true :core:test`.
val coreOnly: Boolean =
    System.getProperty("pp.coreOnly") != null || System.getenv("PP_CORE_ONLY") != null

pluginManagement {
    val coreOnlyPm = System.getProperty("pp.coreOnly") != null || System.getenv("PP_CORE_ONLY") != null
    repositories {
        if (!coreOnlyPm) {
            google {
                content {
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                    includeGroupByRegex("androidx.*")
                }
            }
            mavenCentral()
        } else {
            // Sandbox-Modus: offizieller Maven-Central-Alternativhost (repo.maven.apache.org
            // liefert dort teils 429).
            maven("https://repo1.maven.org/maven2")
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!coreOnly) {
            google {
                content {
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                    includeGroupByRegex("androidx.*")
                }
            }
            mavenCentral()
        } else {
            maven("https://repo1.maven.org/maven2")
        }
    }
}

rootProject.name = "pixelpress"
// Plugins werden einmal im Wurzelprojekt deklariert (apply false), damit Kotlin/AGP/KSP nur in einem
// Classloader geladen werden. Im Kern-Modus gibt es kein AGP, daher eine eigene Wurzel-Build-Datei.
rootProject.buildFileName = if (coreOnly) "build-core.gradle.kts" else "build-root.gradle.kts"
include(":core")
if (!coreOnly) {
    include(":app")
}
