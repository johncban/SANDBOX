// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false

    id("com.google.dagger.hilt.android") version "2.50" apply false
    //id("org.jetbrains.kotlin.android") version "1.9.0" apply false
}

allprojects {
    configurations.all {
        resolutionStrategy {
            // Force use of specific Bouncy Castle version
            force("org.bouncycastle:bcprov-jdk18on:1.77")

            // Exclude old version completely
            exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
        }
    }
}