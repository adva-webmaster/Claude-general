import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
}

val repo = file("../..").absolutePath

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    main {
        kotlin.srcDirs("$repo/app/src/main/kotlin", "$repo/core/src/main/kotlin", "stubs", "gen")
    }
    test {
        kotlin.srcDirs("$repo/app/src/test/kotlin", "$repo/app/src/androidTest/kotlin", "teststubs")
    }
}

val composeMp = "1.7.3"

dependencies {
    implementation("org.jetbrains.compose.material3:material3-desktop:$composeMp")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:$composeMp")
    implementation("org.jetbrains.compose.ui:ui-desktop:$composeMp")
    implementation("org.jetbrains.compose.animation:animation-desktop:$composeMp")
    implementation("org.jetbrains.compose.runtime:runtime-desktop:$composeMp")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("com.squareup.okio:okio-jvm:3.9.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.compose.ui:ui-test-desktop:$composeMp")
    testImplementation("org.jetbrains.compose.ui:ui-test-junit4-desktop:$composeMp")
    compileOnly(files(
        "libs/android-all.jar",
        "libs/coil-android.jar", "libs/coil-core-android.jar", "libs/coil-compose-android.jar",
        "libs/coil-compose-core-android.jar", "libs/coil-video.jar", "libs/osmdroid.jar"
    ))
}

