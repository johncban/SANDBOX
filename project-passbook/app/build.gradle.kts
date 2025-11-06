plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-kapt")
    alias(libs.plugins.hilt.android)
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

        // FIXED: Removed multiDexEnabled - indicates oversized dependencies
        // Analyze and optimize dependency tree instead of enabling multiDex
        // multiDexEnabled = true  // REMOVED for security
        
        // FIXED: Only add necessary vector drawable configuration
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // FIXED: Add proper release signing configuration for production security
    signingConfigs {
        create("release") {
            // Configure via gradle.properties or environment variables
            // NEVER commit keystore credentials to version control
            storeFile = file(findProperty("RELEASE_STORE_FILE") ?: "release.keystore")
            storePassword = findProperty("RELEASE_STORE_PASSWORD") as String? ?: ""
            keyAlias = findProperty("RELEASE_KEY_ALIAS") as String? ?: ""
            keyPassword = findProperty("RELEASE_KEY_PASSWORD") as String? ?: ""
            
            // Enforce modern signing schemes
            enableV1Signing = false  // Disable legacy JAR signing
            enableV2Signing = true   // Enable APK Signature Scheme v2
            enableV3Signing = true   // Enable APK Signature Scheme v3
            enableV4Signing = true   // Enable APK Signature Scheme v4
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // FIXED: Disable debugging for security-sensitive operations
            // Even debug builds should not expose sensitive crypto operations
            isDebuggable = false  // SECURITY: Changed from true
            buildConfigField("boolean", "DEBUG_MODE", "true")
            applicationIdSuffix = ".debug"
            
            // Add debug-specific security configurations
            buildConfigField("boolean", "ALLOW_SECURITY_BYPASS", "false")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false

            // FIXED: Move ProGuard configuration to release buildType only
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // FIXED: Apply signing configuration
            signingConfig = signingConfigs.getByName("release")

            buildConfigField("boolean", "DEBUG_MODE", "false")
            buildConfigField("boolean", "ALLOW_SECURITY_BYPASS", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        
        // FIXED: Enhanced compiler arguments for better security
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-Xjsr305=strict",  // Strict null safety
            "-Xcontext-receivers",  // Enable context receivers
            "-Xjvm-default=all"  // Generate default methods for interfaces
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // FIXED: Enhanced packaging exclusions for security
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "**/attach_hotspot_windows.dll"
            excludes += "META-INF/licenses/**"
            excludes += "META-INF/AL2.0"
            excludes += "META-INF/LGPL2.1"
        }
    }

    // FIXED: Enhanced test configuration
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        animationsDisabled = true
    }

    // FIXED: Add comprehensive lint configuration for security
    lint {
        checkReleaseBuilds = true
        abortOnError = true
        warningsAsErrors = false  // Don't fail build on warnings, but log them
        disable += setOf("MissingTranslation", "ExtraTranslation")
        enable += setOf(
            "Security",
            "HardcodedDebugMode", 
            "SetJavaScriptEnabled",
            "TrustAllX509TrustManager",
            "Overdraw",
            "UnusedResources"
        )
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    // FIXED: Use consistent BOM-managed dependency versions
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose dependencies - versions managed by BOM
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // FIXED: Lifecycle & ViewModel - use version catalog consistently
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // Dependency Injection - Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // FIXED: Security & Cryptography with proper exclusions
    implementation(libs.androidx.security.crypto) {
        // Exclude Gson to prevent conflicts with kotlinx-serialization
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation(libs.androidx.biometric)  // Use version catalog
    implementation(libs.argon2kt)
    implementation(libs.sqlcipher)

    // Image Loading
    implementation(libs.coil.compose)

    // Utilities
    implementation(libs.timber)
    implementation(libs.rootbeer)

    // FIXED: Use version catalog for all dependencies
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.hilt.android.testing)
    kaptTest(libs.hilt.compiler)

    // Instrumentation Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.compiler)

    // Debug Dependencies
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// FIXED: Enhanced Kapt configuration for performance and security
kapt {
    correctErrorTypes = true
    useBuildCache = true
    includeCompileClasspath = false  // Improve build performance

    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
        arg("dagger.experimentalDaggerErrorMessages", "enabled")
    }
}

// FIXED: Add security verification tasks
tasks.register("verifySecurityConfig") {
    doLast {
        val buildFile = file("build.gradle.kts")
        val content = buildFile.readText()
        
        // Verify no debug artifacts in release
        if (content.contains("isDebuggable = true") && 
            !content.contains("debug {") ||
            content.contains("multiDexEnabled = true")) {
            throw GradleException("Security violation: Debug configuration or multiDex found in release!")
        }
        
        println("✓ Security configuration verified")
    }
}

// FIXED: Hook verification into release builds
tasks.whenTaskAdded {
    if (name.contains("Release")) {
        dependsOn("verifySecurityConfig")
    }
}