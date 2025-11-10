plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-kapt")
    alias(libs.plugins.hilt.android)
    id("kotlin-parcelize")
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

        vectorDrawables {
            useSupportLibrary = true
        }

        // BuildConfig fields
        buildConfigField("boolean", "DEBUG_MODE", "false")
        buildConfigField("String", "VERSION_NAME", "\"${versionName}\"")
        buildConfigField("int", "VERSION_CODE", "${versionCode}")
    }

    // FIXED: signingConfigs at android level (properly closed)
    signingConfigs {
        create("release") {
            storeFile = file(findProperty("RELEASE_STORE_FILE") ?: "release.keystore")
            storePassword = findProperty("RELEASE_STORE_PASSWORD") as String? ?: ""
            keyAlias = findProperty("RELEASE_KEY_ALIAS") as String? ?: ""
            keyPassword = findProperty("RELEASE_KEY_PASSWORD") as String? ?: ""
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    // FIXED: splits is now at android {} level, PARALLEL to signingConfigs (not nested inside)
    splits {
        density {
            isEnable = true
            reset()
            include("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        }
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }


    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "DEBUG_MODE", "true")
            buildConfigField("boolean", "ALLOW_SECURITY_BYPASS", "false")
            buildConfigField("String", "BUILD_TYPE", "\"debug\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "DEBUG_MODE", "false")
            buildConfigField("boolean", "ALLOW_SECURITY_BYPASS", "false")
            buildConfigField("String", "BUILD_TYPE", "\"release\"")
            buildConfigField("String", "BUILD_TIMESTAMP", "\"${System.currentTimeMillis()}\"")

            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }

        create("staging") {
            initWith(getByName("release"))
            isDebuggable = true
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("boolean", "DEBUG_MODE", "true")
            buildConfigField("String", "BUILD_TYPE", "\"staging\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-Xjsr305=strict",
            "-Xjvm-default=all"
        )
        allWarningsAsErrors = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = false
        dataBinding = false
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "META-INF/licenses/**",
                "kotlin/**"
            )
            pickFirsts += listOf(
                "**/libc++_shared.so"
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = false
        baseline = file("lint-baseline.xml")
        disable += listOf("MissingTranslation")
    }
}

dependencies {
    // BOM management
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.room.common.jvm)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    // Compose UI
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.runtime.livedata)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.runtime.ktx)

    // Lifecycle & ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Database - Room with SQLCipher
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // Dependency Injection - Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    kapt(libs.hilt.compiler)

    // Security
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.argon2kt)
    implementation(libs.sqlcipher)

    // Background processing
    implementation(libs.androidx.work.runtime.ktx)

    // Image loading
    implementation(libs.coil.compose)

    // Utilities
    implementation(libs.timber)
    implementation(libs.rootbeer)

    // JSON serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

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
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.compiler)

    // Debug tools
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.leakcanary.android)

    // Release optimization
    releaseImplementation(libs.androidx.profileinstaller)
}

kapt {
    correctErrorTypes = true
    useBuildCache = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
    }
}