plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
    // id("kotlin-android") // Redundant if alias(libs.plugins.kotlin.android) is present
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "com.jcb.passbook"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jcb.passbook"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Native library configuration
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = listOf(
            "-Xjvm-default=all",
            "-Xopt-in=kotlin.RequiresOptIn"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }


    packaging {
        // Native library handling
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            // Exclude conflicting files
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // implementation(libs.androidx.espresso.core) // Typically for androidTestImplementation
    // implementation(libs.androidx.animation.core.lint) // Check usage, often not needed directly

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("androidx.compose.material:material-icons-extended:1.7.0") // Version from original was 1.7.6, using 1.7.0 as an example if libs maps to it. Or use "1.7.0" directly.

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    // annotationProcessor("androidx.room:room-compiler:2.6.1") // Not needed if using KAPT
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Dagger Hilt
    implementation("com.google.dagger:hilt-android:2.50") // Original was 2.50
    kapt("com.google.dagger:hilt-compiler:2.50") // Original was 2.50

    // Coroutine support (Coil is for image loading, not directly coroutine support for Retrofit)
    implementation("io.coil-kt:coil-compose:2.5.0") // Original was 1.3.2, common current is higher like 2.5.0

    // Lifecycle and ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0") // Original had 2.8.7, typical version could be 2.8.0 or latest stable
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0") // Original had 2.8.7
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0") // Original had 2.8.7

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7") // Original had 2.8.5, typical version

    // SQLCipher
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")

    // Security Crypto
    implementation("androidx.security:security-crypto:1.1.0-alpha06") { // Original had 1.0.0 and 1.1.0-alpha06. Consolidate to one.
        exclude(group = "com.google.code.gson", module = "gson")
    }

    // Argon2 for password hashing
    implementation("com.lambdapioneer.argon2kt:argon2kt:1.6.0")

    // REMOVED Credential Manager dependencies
    // implementation("androidx.credentials:credentials:1.5.0")
    // implementation("androidx.credentials:credentials-play-services-auth:1.5.0")


    implementation("com.jakewharton.timber:timber:5.0.1")




    implementation("com.scottyab:rootbeer-lib:0.1.1")



    /***
    // Add Firebase Crashlytics dependency
    implementation("com.google.firebase:firebase-crashlytics-ktx:18.4.1")
    implementation("com.google.firebase:firebase-analytics-ktx:21.3.0")
    // Google Services
    apply(plugin = "com.google.gms.google-services")
    ***/


    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    //testImplementation("io.mockk:mockk:1.13.7")
    testImplementation("com.google.truth:truth:1.4.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.0")
    testImplementation("app.cash.turbine:turbine:1.0.0")

    androidTestImplementation("com.google.dagger:hilt-android-testing:2.56.2")
    kaptAndroidTest("com.google.dagger:hilt-compiler:2.56.2")




    // Core testing libraries
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Jetpack Compose UI Testing
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // MockK for mocking in unit tests (a modern alternative to Mockito for Kotlin)
    testImplementation("io.mockk:mockk:1.13.5")

    // Turbine for testing Kotlin Flows
    //testImplementation("app.cash.turbine:turbine:1.0.0")

    // Coroutines testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Hilt for dependency injection in tests
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.48")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.48")
    testImplementation(kotlin("test"))



    // Core Android
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Jetpack Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // Jetpack Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Crypto Libraries
    // implementation("org.bouncycastle:bcprov-jdk15on:1.70")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // DI: Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.mockito:mockito-core:5.2.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")


    // Gson for type conversion
    implementation("com.google.code.gson:gson:2.10.1")


}

// Kapt configuration
kapt {
    correctErrorTypes = true
    useBuildCache = false // Force fresh compilation

    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "false") // Disable incremental for now
        arg("room.expandProjection", "true")
    }
}
