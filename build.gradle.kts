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
        // What the Marketplace shows as "What's new": the section of
        // CHANGELOG.md that carries the version being built. Rendered while the
        // build configures - a provider would have to carry a reference to this
        // script, which the configuration cache cannot store.
        changeNotes = changeNotesFor(
            providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText.get(),
            providers.gradleProperty("pluginVersion").get(),
        )
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    publishing {
        // Marketplace token, from https://plugins.jetbrains.com/author/me/tokens
        token = providers.environmentVariable("PUBLISH_TOKEN")
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

/**
 * Renders the `## [version]` section of a Keep a Changelog file as the HTML the
 * Marketplace expects. Everything below a `### Group` heading becomes one list,
 * unknown lines are passed through as paragraphs.
 *
 * Hand-rolled rather than pulling in the Gradle changelog plugin: that one
 * reaches back into the project from inside the task input, which the
 * configuration cache rejects.
 */
fun changeNotesFor(markdown: String, version: String): String {
    val lines = markdown.lineSequence().toList()
    val start = lines.indexOfFirst { it.startsWith("## [$version]") }
    if (start < 0) return ""
    val body = lines.drop(start + 1).takeWhile { !it.startsWith("## ") }

    val html = StringBuilder()
    var inList = false
    fun closeList() {
        if (inList) html.append("</ul>")
        inList = false
    }

    for (line in body) {
        val text = line.trim()
        when {
            text.isEmpty() -> Unit

            text.startsWith("### ") -> {
                closeList()
                html.append("<b>").append(inline(text.removePrefix("### "))).append("</b>")
            }

            text.startsWith("- ") -> {
                if (!inList) html.append("<ul>")
                inList = true
                html.append("<li>").append(inline(text.removePrefix("- "))).append("</li>")
            }

            // Wrapped continuation of the entry above.
            inList -> html.insert(html.length - "</li>".length, " " + inline(text))

            else -> html.append("<p>").append(inline(text)).append("</p>")
        }
    }
    closeList()
    return html.toString()
}

/** Escapes HTML and turns `code spans` into &lt;code&gt; elements. */
fun inline(text: String): String {
    val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return Regex("`([^`]+)`").replace(escaped) { "<code>" + it.groupValues[1] + "</code>" }
}
