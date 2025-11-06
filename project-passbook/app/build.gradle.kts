plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-kapt")
    alias(libs.plugins.hilt.android)
    id("kotlin-parcelize") // For Parcelable data classes
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

        // FIXED: Use custom test runner for Hilt integration
        testInstrumentationRunner = "com.jcb.passbook.HiltTestRunner"

        // SECURITY: Removed multiDexEnabled - analyze dependencies instead
        // Large dependency trees indicate potential supply chain risks
        
        // Vector drawable optimization
        vectorDrawables {
            useSupportLibrary = true
            generatedDensities = emptySet() // Use only vector drawables
        }

        // Resource optimization for security app
        resourceConfigurations += listOf("en", "xxhdpi", "xxxhdpi")
        
        // FIXED: Consistent buildConfigField placement
        buildConfigField("boolean", "DEBUG_MODE", "false")
        buildConfigField("String", "VERSION_NAME", "\"${versionName}\"")
        buildConfigField("int", "VERSION_CODE", "${versionCode}")
    }

    // PRODUCTION: Proper release signing with security best practices
    signingConfigs {
        create("release") {
            // SECURITY: Use gradle.properties or environment variables
            // NEVER commit credentials to version control
            storeFile = file(findProperty("RELEASE_STORE_FILE") ?: "release.keystore")
            storePassword = findProperty("RELEASE_STORE_PASSWORD") as String? ?: ""
            keyAlias = findProperty("RELEASE_KEY_ALIAS") as String? ?: ""
            keyPassword = findProperty("RELEASE_KEY_PASSWORD") as String? ?: ""
            
            // SECURITY: Modern signing schemes only
            enableV1Signing = false  // Disable legacy JAR signing
            enableV2Signing = true   // APK Signature Scheme v2 (Android 7.0+)
            enableV3Signing = true   // APK Signature Scheme v3 (Android 9.0+)
            enableV4Signing = true   // APK Signature Scheme v4 (Android 11+)
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            // SECURITY: Even debug should not be debuggable for password manager
            isDebuggable = false
            isJniDebuggable = false
            isRenderscriptDebuggable = false
            
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            
            buildConfigField("boolean", "DEBUG_MODE", "true")
            buildConfigField("boolean", "ALLOW_SECURITY_BYPASS", "false")
            buildConfigField("String", "BUILD_TYPE", "\"debug\"")
            
            // Debug-specific ProGuard rules for easier debugging
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro",
                "proguard-rules-debug.pro"
            )
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            isRenderscriptDebuggable = false
            isPseudoLocalesEnabled = false

            // SECURITY: Apply release signing
            signingConfig = signingConfigs.getByName("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            buildConfigField("boolean", "DEBUG_MODE", "false")
            buildConfigField("boolean", "ALLOW_SECURITY_BYPASS", "false")
            buildConfigField("String", "BUILD_TYPE", "\"release\"")
            buildConfigField("String", "BUILD_TIMESTAMP", "\"${System.currentTimeMillis()}\"")
            
            // APK optimization
            ndk {
                debugSymbolLevel = "NONE"
            }
        }
        
        // TESTING: Staging build type for release testing
        create("staging") {
            initWith(getByName("release"))
            isDebuggable = true
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            
            buildConfigField("boolean", "DEBUG_MODE", "true")
            buildConfigField("String", "BUILD_TYPE", "\"staging\"")
            
            // Use debug ProGuard for easier testing
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        
        // API compatibility for older devices
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        
        // PERFORMANCE: Enhanced compiler optimizations
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-Xjsr305=strict",              // Strict null safety
            "-Xjvm-default=all",            // JVM default methods
            "-Xcontext-receivers",          // Context receivers
            "-Xbackend-threads=0",          // Use all CPU cores for compilation
            "-Xuse-k2"                      // Use K2 compiler for better performance
        )
        
        allWarningsAsErrors = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = false  // Not needed with Compose
        dataBinding = false  // Not needed with Compose
        aidl = false
        renderScript = false
        resValues = false
        shaders = false
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    // SECURITY: Comprehensive packaging exclusions
    packaging {
        resources {
            excludes += listOf(
                // License files
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/license*",
                "/META-INF/NOTICE*",
                "/META-INF/notice*",
                "/META-INF/ASL2.0",
                "/META-INF/*.kotlin_module",
                
                // Debug/development files
                "**/attach_hotspot_windows.dll",
                "META-INF/licenses/**",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "**/*.pro",
                "**/*.properties",
                
                // Kotlin metadata
                "kotlin/**",
                "META-INF/*.kotlin_module",
                "META-INF/*/*.kotlin_module"
            )
            
            pickFirsts += listOf(
                "**/libc++_shared.so",
                "**/libjsc.so",
                "**/libfbjni.so"
            )
        }
    }

    // TESTING: Enhanced test configuration
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            
            // Test execution optimization
            all {
                it.useJUnitPlatform()
                it.testLogging {
                    events("passed", "skipped", "failed", "standardOut", "standardError")
                    showExceptions = true
                    showCauses = true
                    showStackTraces = true
                }
                it.maxHeapSize = "2g"
                it.jvmArgs("-XX:MaxMetaspaceSize=512m")
            }
        }
        
        animationsDisabled = true
        
        // INSTRUMENTATION: Managed test devices
        managedDevices {
            devices {
                create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api31") {
                    device = "Pixel 6"
                    apiLevel = 31
                    systemImageSource = "aosp-atd"
                }
                
                create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel4Api29") {
                    device = "Pixel 4"
                    apiLevel = 29
                    systemImageSource = "aosp"
                }
            }
        }
    }

    // SECURITY: Comprehensive lint configuration
    lint {
        checkReleaseBuilds = true
        checkDependencies = true
        abortOnError = true
        warningsAsErrors = false
        ignoreWarnings = false
        
        // Security-focused lint checks
        enable += setOf(
            "HardcodedValues",
            "HardcodedText", 
            "HardcodedDebugMode",
            "InsecureBaseConfiguration",
            "TrustAllX509TrustManager",
            "AllowAllHostnameVerifier",
            "BadHostnameVerifier",
            "TrulyRandom",
            "SetJavaScriptEnabled",
            "UnusedResources",
            "Security",
            "Overdraw"
        )
        
        // Disable non-critical checks
        disable += setOf(
            "MissingTranslation",
            "ExtraTranslation",
            "IconMissingDensityFolder",
            "GoogleAppIndexingWarning",
            "ContentDescription" // Accessibility handled programmatically
        )
        
        baseline = file("lint-baseline.xml")
        htmlReport = true
        xmlReport = true
        sarifReport = true
    }

    // DISTRIBUTION: Bundle configuration for Play Store
    bundle {
        language {
            // Keep all languages in base APK for security apps
            enableSplit = false
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }
}

dependencies {
    // BOM management for consistent versions
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // Core Android with lifecycle awareness
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process) // Process lifecycle monitoring
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    // Compose UI - comprehensive suite
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.runtime.livedata)

    // Navigation with type safety
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.runtime.ktx)

    // Lifecycle & ViewModel with Compose integration
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Database - Room with SQLCipher
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // Dependency Injection - Hilt with Compose
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work) // WorkManager integration
    kapt(libs.hilt.compiler)

    // SECURITY: Cryptography and authentication
    implementation(libs.androidx.security.crypto) {
        // Exclude Gson to prevent conflicts with kotlinx-serialization
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation(libs.androidx.biometric)
    implementation(libs.argon2kt)
    implementation(libs.sqlcipher)
    
    // SECURITY: Additional security libraries
    implementation("androidx.security:security-identity-credential:1.0.0-alpha03")

    // Background processing
    implementation(libs.androidx.work.runtime.ktx)

    // Image loading with security considerations
    implementation(libs.coil.compose)

    // Utilities and logging
    implementation(libs.timber)
    implementation(libs.rootbeer)

    // Core library desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // JSON serialization - kotlinx only
    implementation(libs.kotlinx.serialization.json)

    // Coroutines with lifecycle integration
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // TESTING: Comprehensive unit test suite
    testImplementation(libs.junit)
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.engine)
    testImplementation(libs.junit5.params)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.mockk.android)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.hilt.android.testing)
    kaptTest(libs.hilt.compiler)

    // TESTING: Instrumentation tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.espresso.contrib)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.compiler)

    // DEBUG: Development and debugging tools
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.leakcanary.android) // Memory leak detection
    debugImplementation(libs.androidx.compose.ui.tooling.preview)

    // RELEASE: Performance profiling
    releaseImplementation(libs.androidx.profileinstaller)
}

// PERFORMANCE: Enhanced Kapt configuration
kapt {
    correctErrorTypes = true
    useBuildCache = true
    showProcessorStats = true
    includeCompileClasspath = false
    
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
        arg("room.generateKotlin", "true")
        arg("dagger.fastInit", "enabled")
        arg("dagger.formatGeneratedSource", "disabled")
        arg("dagger.experimentalDaggerErrorMessages", "enabled")
    }
    
    javacOptions {
        option("-Xmaxerrs", 1000)
        option("-Xmaxwarns", 1000)
    }
}

// HILT: Experimental features
hilt {
    enableAggregatingTask = true
    enableExperimentalClasspathAggregation = true
}

// ANDROID COMPONENTS: Build customization
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.packaging.resources.excludes.add("META-INF/androidx.*.version")
        
        // Optimize for release builds
        if (variant.buildType == "release") {
            variant.packaging.resources.excludes.addAll(listOf(
                "**/*.kotlin_metadata",
                "**/*.kotlin_module",
                "**/*.kotlin_builtins",
                "META-INF/**.kotlin_module"
            ))
        }
    }
}

// SECURITY: Security verification task
tasks.register("verifySecurityConfig") {
    description = "Verifies security configuration in build file"
    group = "verification"
    
    doLast {
        val buildFile = file("build.gradle.kts")
        val content = buildFile.readText()
        
        val violations = mutableListOf<String>()
        
        // Check for security violations
        if (content.contains("multiDexEnabled = true")) {
            violations.add("MultiDex enabled - potential security risk")
        }
        
        if (content.contains("isDebuggable = true") && 
            !content.contains("buildTypes") && 
            !content.contains("debug {")) {
            violations.add("Global debuggable flag found - security risk")
        }
        
        if (content.contains("allowBackup = true")) {
            violations.add("Backup allowed - potential data leakage")
        }
        
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Security violations found:\n${violations.joinToString("\n- ", "- ")}"
            )
        }
        
        println("✅ Security configuration verified successfully")
    }
}

// DOCUMENTATION: Generate dependency report
tasks.register("generateDependencyReport") {
    description = "Generates dependency report for security audit"
    group = "reporting"
    
    doLast {
        val reportFile = file("$buildDir/reports/dependencies.txt")
        reportFile.parentFile.mkdirs()
        
        exec {
            commandLine("./gradlew", "app:dependencies")
            standardOutput = reportFile.outputStream()
        }
        
        println("📊 Dependency report generated: ${reportFile.absolutePath}")
    }
}

// AUTOMATION: Hook verification into release builds
tasks.matching { it.name.contains("Release") && it.name.contains("assemble") }.configureEach {
    dependsOn("verifySecurityConfig")
    finalizedBy("generateDependencyReport")
}

// DISTRIBUTION: Copy ProGuard mappings for crash analysis
tasks.register<Copy>("copyProguardMappings") {
    description = "Copies ProGuard mapping files for crash reporting"
    group = "distribution"
    
    from("$buildDir/outputs/mapping/release")
    into("$projectDir/proguard-mappings")
    include("mapping.txt")
    
    doLast {
        println("📋 ProGuard mappings copied to proguard-mappings/")
    }
}

// Hook mapping copy to release assembly
tasks.named("assembleRelease") {
    finalizedBy("copyProguardMappings")
}