// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android) apply false
}

// Configure all Kotlin compilation tasks
allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs += listOf(
                "-Xjsr305=strict",
                "-opt-in=kotlin.RequiresOptIn",
                "-Xjvm-default=all"
            )
            // REMOVED: progressiveMode (not available in all Kotlin versions)
            // Use instead: enable it via gradle.properties with kotlin.progressive=true
            allWarningsAsErrors = false
        }
    }
}

// Standard clean task
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

// Java compilation warnings
tasks.withType<JavaCompile>().configureEach {
    options.apply {
        compilerArgs.addAll(listOf(
            "-Xlint:unchecked",
            "-Xlint:deprecation"
        ))
        encoding = "UTF-8"
    }
}

// Custom tasks for project management
tasks.register("printVersions") {
    group = "help"
    description = "Prints all dependency versions"

    doLast {
        println("=".repeat(60))
        println("PROJECT DEPENDENCY VERSIONS")
        println("=".repeat(60))
        println("Compile SDK: ${findProperty("android.compileSdk") ?: "Not set"}")
        println("Min SDK: ${findProperty("android.minSdk") ?: "Not set"}")
        println("Target SDK: ${findProperty("android.targetSdk") ?: "Not set"}")
        println("Kotlin JVM Target: 17")
        println("=".repeat(60))
    }
}

// Task to verify project structure
tasks.register("verifyProject") {
    group = "verification"
    description = "Verifies project structure and configuration"

    doLast {
        val requiredFiles = listOf(
            "gradle.properties",
            "settings.gradle.kts",
            "local.properties"
        )

        requiredFiles.forEach { file ->
            val exists = rootProject.file(file).exists()
            println("${if (exists) "✅" else "❌"} $file")
        }
    }
}