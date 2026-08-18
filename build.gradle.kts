import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin { jvmToolchain(21) }

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion"),
        )
        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            // Lower bound of the supported range.
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1")
            // Optionally an IDE installed on this machine - see gradle.properties.
            providers.gradleProperty("verifyAgainstLocalIde").orNull
                ?.takeIf { file(it).isDirectory }
                ?.let(::local)
        }
    }
}

tasks {
    wrapper { gradleVersion = "9.3.1" }
}
