plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
}

// Gradle's Windows test-worker argfile can corrupt non-ASCII classpath entries.
// Keep source files in their original location, but place disposable build output
// under an ASCII-only path when the checkout directory contains non-ASCII text.
if (rootDir.absolutePath.any { it.code > 127 }) {
    val safeBuildRoot = gradle.gradleUserHomeDir.resolve("project-builds/savor-notes")
    allprojects {
        val projectFolder = if (path == ":") "root" else path.removePrefix(":").replace(':', '-')
        layout.buildDirectory.set(safeBuildRoot.resolve(projectFolder))
    }
}
