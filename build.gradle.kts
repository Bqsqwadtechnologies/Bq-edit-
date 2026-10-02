buildscript {
    dependencies {
        // AGP 9 provides built-in Kotlin. Pin the Kotlin Gradle plugin to the
        // same Kotlin line used by the Compose compiler plugin.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
