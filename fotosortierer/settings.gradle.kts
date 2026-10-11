// Kern-Modus: Mit -Dfoto.coreOnly=true (oder Umgebungsvariable FOTO_CORE_ONLY=1) wird nur das
// reine Kotlin-Modul :core gebaut. Das ist nützlich in Umgebungen ohne Android-SDK/Google-Maven
// (z. B. CI-Sandboxes): `./gradlew -Dfoto.coreOnly=true :core:test`.
val coreOnly: Boolean =
    System.getProperty("foto.coreOnly") != null || System.getenv("FOTO_CORE_ONLY") != null

pluginManagement {
    val coreOnlyPm = System.getProperty("foto.coreOnly") != null || System.getenv("FOTO_CORE_ONLY") != null
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

rootProject.name = "fotosortierer"
include(":core")
if (!coreOnly) {
    include(":app")
}
