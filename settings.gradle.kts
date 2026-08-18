plugins {
    // Lets Gradle fetch the JDK 21 toolchain itself instead of requiring a
    // matching JDK to be installed up front.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "claude-chappe"
